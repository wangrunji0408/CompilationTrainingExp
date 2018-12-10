
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
  void visitModule(Module &M) {}
  void visitFunction(Function &F) {}
  void visitBasicBlock(BasicBlock &B) {}

  void visitAdd(BinaryOperator &I) {}
  void visitSub(BinaryOperator &I) {}
  void visitMul(BinaryOperator &I) {}
  void visitShl(BinaryOperator &I) {}
  void visitLShr(BinaryOperator &I) {}
  void visitAShr(BinaryOperator &I) {}
  void visitAnd(BinaryOperator &I) {}
  void visitOr(BinaryOperator &I) {}
  void visitXor(BinaryOperator &I) {}
  void visitICmp(ICmpInst &I) {}

  void visitBranchInst(BranchInst &I) {}
  void visitPHINode(PHINode &I) {}

  // Call checkAndReport here.
  void visitGetElementPtrInst(GetElementPtrInst &I) {}
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
