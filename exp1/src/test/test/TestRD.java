package test;

public class TestRD {
    int foo() {
        int a, b, c, d, e;
        a = 1;
        b = 2;
        while (a > 1) {
            c = a + b;
            d = c - a;
            while (a > 1) {
                d = b + d;
                if (true) break;
                d = a + b;
            }
            b = a + b;
            e = c - a;
            b = b * d;
        }
        return a + b;
    }
}
