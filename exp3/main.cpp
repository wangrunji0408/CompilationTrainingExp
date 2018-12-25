
#include <map>
#include <string>

#include <llvm/IR/CFG.h>
#include <llvm/IR/InstVisitor.h>
#include <llvm/IRReader/IRReader.h>
#include <llvm/Support/SourceMgr.h>
#include <llvm/Support/raw_ostream.h>
#include <z3++.h>

using namespace llvm;

namespace {

// Get unique name of a LLVM node. Applicable to BasicBlock and Instruction.
std::string getName(const Value &Node) {
  if (!Node.getName().empty())
    return Node.getName().str();

  std::string Str;
  raw_string_ostream OS(Str);

  Node.printAsOperand(OS, false);
  return OS.str();
}

// Check
void checkAndReport(z3::solver &solver, const GetElementPtrInst &gep) {
  std::string name = getName(gep);
  std::cout << "Checking with assertions:" << std::endl
            << solver.assertions() << std::endl;
  if (solver.check() == z3::sat)
    std::cout << "GEP " << name << " is potentially out of bound." << std::endl
              << "Model causing out of bound:" << std::endl
              << solver.get_model() << std::endl;
  else
    std::cout << "GEP " << name << " is safe." << std::endl;
}
} // namespace

// ONLY MODIFY THIS CLASS FOR PART 1 & 2!
class Z3Walker : public InstVisitor<Z3Walker> {
private:
  std::map<std::string, std::vector<z3::expr>> predicate_map;
  z3::context ctx;
  z3::solver solver;

public:
  Z3Walker() : ctx(), solver(ctx) {}

  // Not using InstVisitor::visit due to their sequential order.
  // We want topological order on the Call Graph and CFG.
  void visitModule(Module &M) {
    for(auto &f: M.getFunctionList()) {
      visitFunction(f);
    }
  }
  void visitFunction(Function &F) {
    std::cerr << "Function " << F.getName().str() << std::endl;
    solver.reset();
    for(auto &bb: F.getBasicBlockList()) {
      visitBasicBlock(bb);
    }
  }
  void visitBasicBlock(BasicBlock &B) {
    std::cerr << "BasicBlock " << B.getName().str() << std::endl;
    for(auto &inst: B.getInstList()) {
      this->visit(inst);
    }
  }

  void visitZExtInst(ZExtInst &I) {
    auto dst_sz = I.getDestTy()->getIntegerBitWidth();
    auto dst = ctx.bv_const(I.getName().str().c_str(), dst_sz);
    auto src_sz = I.getSrcTy()->getIntegerBitWidth();
    auto src = ctx.bv_const(I.getOperand(0)->getName().str().c_str(), src_sz);
    auto e = dst == z3::zext(src, dst_sz - src_sz);
    solver.add(e);
  }

  void visitSExtInst(SExtInst &I) {
    auto dst_sz = I.getDestTy()->getIntegerBitWidth();
    auto dst = ctx.bv_const(I.getName().str().c_str(), dst_sz);
    auto src_sz = I.getSrcTy()->getIntegerBitWidth();
    auto src = ctx.bv_const(I.getOperand(0)->getName().str().c_str(), src_sz);
    auto e = dst == z3::sext(src, dst_sz - src_sz);
    solver.add(e);
  }

  void visitBinaryOperator(BinaryOperator &I) {
    if(!isSupportedBinOp(I.getOpcode()))
      return;
    auto dst = ctx.bv_const(I.getName().str().c_str(), 32);
    auto e1 = operandToZ3(I.getOperand(0));
    auto e2 = operandToZ3(I.getOperand(1));
    auto e = dst == binOpToZ3(I.getOpcode(), e1, e2);
    solver.add(e);
  }

  void visitICmp(ICmpInst &I) {
    auto dst = ctx.bv_const(I.getName().str().c_str(), 1);
    auto e1 = operandToZ3(I.getOperand(0));
    auto e2 = operandToZ3(I.getOperand(1));
    auto cond = predicateToZ3(I.getPredicate(), e1, e2);
    auto e = dst == z3::ite(cond, ctx.bv_val(1, 1), ctx.bv_val(0, 1));
    solver.add(e);
  }

  z3::expr operandToZ3(llvm::Value* value) {
    if(auto ci = dyn_cast<llvm::ConstantInt>(value)) {
      auto val = ci->getValue().getSExtValue();
      auto sz = ci->getType()->getIntegerBitWidth();
      return ctx.bv_val(val, sz);
    } else {
      return ctx.bv_const(value->getName().str().c_str(), 32);
    }
  }

  static bool isSupportedBinOp(Instruction::BinaryOps const &op) {
    switch(op) {
      case Instruction::BinaryOps::Add:   return true;
      case Instruction::BinaryOps::Sub:   return true;
      case Instruction::BinaryOps::Mul:   return true;
      case Instruction::BinaryOps::Shl:   return true;
      case Instruction::BinaryOps::LShr:  return true;
      case Instruction::BinaryOps::AShr:  return true;
      case Instruction::BinaryOps::And:   return true;
      case Instruction::BinaryOps::Or:    return true;
      case Instruction::BinaryOps::Xor:   return true;
      default: return false;
    }
  }

  static z3::expr binOpToZ3(Instruction::BinaryOps const &op, z3::expr const &e1, z3::expr const &e2) {
    switch(op) {
      case Instruction::BinaryOps::Add:   return e1 + e2;
      case Instruction::BinaryOps::Sub:   return e1 - e2;
      case Instruction::BinaryOps::Mul:   return e1 * e2;
      case Instruction::BinaryOps::Shl:   return z3::shl(e1, e2);
      case Instruction::BinaryOps::LShr:  return z3::lshr(e1, e2);
      case Instruction::BinaryOps::AShr:  return z3::ashr(e1, e2);
      case Instruction::BinaryOps::And:   return e1 & e2;
      case Instruction::BinaryOps::Or:    return e1 | e2;
      case Instruction::BinaryOps::Xor:   return e1 ^ e2;
      default: throw std::exception();
    }
  }

  static z3::expr predicateToZ3(CmpInst::Predicate const &p, z3::expr const &e1, z3::expr const &e2) {
    switch(p) {
      case CmpInst::Predicate::ICMP_EQ:  return e1 == e2;
      case CmpInst::Predicate::ICMP_NE:  return e1 != e2;
      case CmpInst::Predicate::ICMP_ULT: return e1 <  e2;
      case CmpInst::Predicate::ICMP_ULE: return e1 <= e2;
      case CmpInst::Predicate::ICMP_UGT: return e1 >  e2;
      case CmpInst::Predicate::ICMP_UGE: return e1 >= e2;
      case CmpInst::Predicate::ICMP_SLT: return e1 <  e2;
      case CmpInst::Predicate::ICMP_SLE: return e1 <= e2;
      case CmpInst::Predicate::ICMP_SGT: return e1 >  e2;
      case CmpInst::Predicate::ICMP_SGE: return e1 >= e2;
      default: throw std::exception();
    }
  }

  void visitBranchInst(BranchInst &I) {}
  void visitPHINode(PHINode &I) {}

  // Call checkAndReport here.
  void visitGetElementPtrInst(GetElementPtrInst &I) {
    checkAndReport(solver, I);
  }
};

int main(int argc, char const *argv[]) {
  if (argc < 2) {
    errs() << "Usage: " << argv[0] << " <IR file>\n";
    return 1;
  }

  LLVMContext llvmctx;

  // Parse the input LLVM IR file into a module.
  SMDiagnostic Err;
  auto module = parseIRFile(argv[1], Err, llvmctx);
  if (!module) {
    Err.print(argv[0], errs());
    return 1;
  }

  Z3Walker().visitModule(*module);

  return 0;
}
