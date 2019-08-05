import org.junit.Test;
import org.junit.Before; 
import org.junit.After;

import static org.junit.Assert.*;

/** 
* RateLimiter Tester. 
* 
* @author <Authors name> 
* @since <pre>8�� 5, 2019</pre> 
* @version 1.0 
*/ 
public class RateLimiterTest { 

    private RateLimiter limiter;

    @Before
    public void before() throws Exception {
        limiter = new RateLimiter(1000, 20, 100);
    }

    @After
    public void after() throws Exception {
    }

    @Test
    public void checkArguments() {
        assertEquals(limiter.getIncrement(), 20);
        assertEquals(limiter.getInterval(), 1000);
        assertEquals(limiter.getMaxCapacity(), 100);
    }

    @Test
    public void checkInitialCapacity() {
        assertEquals(limiter.getCapacity(), 0);
    }


} 
