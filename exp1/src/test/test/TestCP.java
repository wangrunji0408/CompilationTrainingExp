package test;

public class TestCP {
    int foo() {
        int a = 3;
        int b = a + 1;
        int c = -b;
        int d;
        if (a > 3) {
            d = 0;
            while (c > 10) {
                d = c + 4;
            }
        } else
            d = 0;
        return a + b + c + d;
    }
}
