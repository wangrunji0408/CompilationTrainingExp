package test;

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
    int test2(int y) {
        int x = 2;
        int z = x + y;
        y = Integer.valueOf("123");
        return y;
    }

    void test2_(int y) {
        int x = 2;
        int z = x + y;
        y = Integer.valueOf("123");
        System.out.println("res: " + y);
    }

    int test3() {
        int x = Integer.valueOf("100");
        int y = Integer.valueOf("3");
        if (Integer.valueOf("1") == 1)
            y = x + 1;
        return y;
    }

    int test4() {
        int x = Integer.valueOf("100");
        int y = Integer.valueOf("10");
        int z = Integer.valueOf("1");
        int c = Integer.valueOf("1");
        while (x < 100) {
            x = x + y;
            while (y < 100) {
                y = y + z;
                while (z < 100) {
                    z = z + c;
                }
            }
        }
        return x;
    }
}
