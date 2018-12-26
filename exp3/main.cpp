
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
  std::map<std::string, std::map<std::string, z3::expr>> predicate_map;
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

  // 流图遍历策略：
  // * 前向：从entry BB开始dfs
  // * 访问每个BB时，首先检查是否已经访问过所有前驱
  //   若是，再继续向后走
  // * 每个BB记录：从每个前驱到来时的条件z3::expr
  void visitFunction(Function &F) {
    std::cerr << "Function " << F.getName().str() << std::endl;
    // reset
    solver.reset();
    predicate_map.clear();
    visitBasicBlock(F.getEntryBlock());
  }

  void visitBasicBlock(BasicBlock &B) {
    // ensure all predecessors have been visited
    if(predicate_map[getName(B)].size() != count_predecessors(B))
      return;
    std::cerr << "BasicBlock " << B.getName().str() << std::endl;

    for(auto &inst: B.getInstList()) {
      this->visit(inst);
      // visit each successor BB when visiting the last inst (br)
    }
  }

  static int count_predecessors(BasicBlock const& B) {
    int c = 0;
    for(auto _: predecessors(&B)) {
      c += 1;
    }
    return c;
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
    auto dst = ctx.bv_const(I.getName().str().c_str(), I.getType()->getIntegerBitWidth());
    auto e1 = valueToZ3(I.getOperand(0));
    auto e2 = valueToZ3(I.getOperand(1));
    auto e = dst == binOpToZ3(I.getOpcode(), e1, e2);
    solver.add(e);
  }

  void visitICmp(ICmpInst &I) {
    auto dst = ctx.bv_const(I.getName().str().c_str(), 1);
    auto e1 = valueToZ3(I.getOperand(0));
    auto e2 = valueToZ3(I.getOperand(1));
    auto cond = predicateToZ3(I.getPredicate(), e1, e2);
    auto e = dst == z3::ite(cond, ctx.bv_val(1, 1), ctx.bv_val(0, 1));
    solver.add(e);
  }

  z3::expr valueToZ3(llvm::Value *value) {
    auto sz = value->getType()->getIntegerBitWidth();
    if(auto ci = dyn_cast<llvm::ConstantInt>(value)) {
      return ctx.bv_val(ci->getValue().getSExtValue(), sz);
    } else {
      return ctx.bv_const(value->getName().str().c_str(), sz);
    }
  }

  static bool isSupportedBinOp(Instruction::BinaryOps const &op) {
    switch(op) {
      case Instruction::BinaryOps::Add:
      case Instruction::BinaryOps::Sub:
      case Instruction::BinaryOps::Mul:
      case Instruction::BinaryOps::Shl:
      case Instruction::BinaryOps::LShr:
      case Instruction::BinaryOps::AShr:
      case Instruction::BinaryOps::And:
      case Instruction::BinaryOps::Or:
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

  /// Get the predicate of the whole BasicBlock:
  /// the union of all predicates from each predecessors
  z3::expr merge_predicate(BasicBlock const& B) {
    if(predicate_map[getName(B)].empty()) {
      return ctx.bool_val(true);  // then && ...
    }
    auto e = ctx.bool_val(false);
    for(auto const& pair: predicate_map[getName(B)]) {
      e = e || pair.second;
    }
    return e.simplify();
  }

  void visitBranchInst(BranchInst &I) {
    auto bb = getName(*I.getParent());
    auto predicate = merge_predicate(*I.getParent());

    if(I.isConditional()) {
      auto cmp = ctx.bv_const(I.getOperand(0)->getName().str().c_str(), 1);
      auto &tb = *I.getSuccessor(0);
      auto &fb = *I.getSuccessor(1);
      predicate_map[getName(tb)].insert(std::make_pair(bb, predicate && (cmp == 1)));
      visitBasicBlock(tb);
      predicate_map[getName(fb)].insert(std::make_pair(bb, predicate && (cmp == 0)));
      visitBasicBlock(fb);
    } else {
      assert(bb != "entry");
      auto &b = *I.getSuccessor(0);
      predicate_map[getName(b)].insert(std::make_pair(bb, predicate));
      visitBasicBlock(b);
    }
  }

  void visitPHINode(PHINode &I) {
    auto dst = ctx.bv_const(I.getName().str().c_str(), I.getType()->getIntegerBitWidth());

    for(auto bb: I.blocks()) {
      auto const& pred = predicate_map[getName(*I.getParent())].find(getName(*bb))->second;
      auto src = valueToZ3(I.getIncomingValueForBlock(bb));
      auto e = z3::implies(pred, dst == src);
      solver.add(e);
    }
  }

  // Call checkAndReport here.
  void visitGetElementPtrInst(GetElementPtrInst &I) {
    // only check 'inbounds' && type as [N x i32]
    if(!I.isInBounds())
        return;
    if(!I.getSourceElementType()->getArrayElementType()->isIntegerTy(32))
        return;
    solver.push();
    {
      auto pred = merge_predicate(*I.getParent());
      solver.add(pred);

      auto len = (int)I.getSourceElementType()->getArrayNumElements();
      auto idx = ctx.bv_const(I.getOperand(2)->getName().str().c_str(), 64);
      auto out_of_bound = idx < 0 || idx >= len;
      solver.add(out_of_bound);

      checkAndReport(solver, I);
    }
    solver.pop();
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
