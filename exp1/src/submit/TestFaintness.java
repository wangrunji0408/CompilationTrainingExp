package submit;

class TestFaintness {
    /**
     * In this method all variables are faint because the final value is never used.
     * Sample out is at src/test/Faintness.out
     */
    void test1() {
        int x = 2;
        int y = x + 2;
        int z = x + y;
        return;
    }

    /**
     * Write your test cases here. Create as many methods as you want.
     * Run the test from root dir using
     * ./run.sh flow.Flow submit.MySolver submit.Faintness submit.TestFaintness
     */
    int test2(int x, int y, int z, int w) {
        // x: Not Faint. Used by a (Not Faint).
        // y: Not Faint. Used by a (Not Faint) & b (Faint).
        // z: Faint. Used by b (Faint).
        // w: Not Faint. Used by c (Faint). But Unary is not considered.
        int a = x + y;  // a: Not Faint. Used by return.
        int b = y + z;  // b: Faint. Not used.
        int c = -w;     // c: Faint. Not used.
        return a;
    }
}
