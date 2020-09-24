import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TestStack {
    @Test(timeout = 4000)
    public void test() throws Throwable {
        Stack<String> stack = new Stack<String>();
        stack.push("meow");
        assertFalse(stack.isEmpty());
    }
}