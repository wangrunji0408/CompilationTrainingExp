
@a = external local_unnamed_addr global [0 x i32], align 4

define i32 @foo(i32 %i) local_unnamed_addr #0 {
entry:
  %a = alloca i32, i32 1024
  %cmp = icmp ult i32 %i, 1024
  br i1 %cmp, label %if.then, label %return

if.then:                                          ; preds = %entry
  %gep = getelementptr inbounds i32, i32* %a, i32 %i
  %ai = load i32, i32* %gep
  br label %return

return:                                           ; preds = %entry, %if.then
  %ret = phi i32 [ %ai, %if.then ], [ -1, %entry ]
  ret i32 %ret
}
