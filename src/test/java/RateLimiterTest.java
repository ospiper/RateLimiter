import io.reactivex.Observable;
import io.reactivex.observers.DisposableObserver;
import org.junit.Test;
import org.junit.Before;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** 
* RateLimiter Tester. 
* 
* @author LLAP
* @since <pre>Aug 5, 2019</pre>
* @version 1.0 
*/ 
public class RateLimiterTest { 

    private RateLimiter limiter;
    private final long interval = 1000;
    private final long increment = 20;
    private final long maxCapacity = 100;
    private long fillTime = 0;

    @Before
    public void before() {
        limiter = new RateLimiter(interval, increment, maxCapacity);
        limiter.stopSupply();
        limiter.debug = true;
        fillTime = (int)Math.ceil((double)maxCapacity / increment);
    }

    /**
     * Wait until bucket is full
     * @throws Exception Thread interrupted
     */
    private void waitUntilFull() throws Exception {
        try {
            long shouldSleep = (long)(fillTime * interval * 1.05);
            TimeUnit.MILLISECONDS.sleep(shouldSleep);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    @Test
    public void checkArguments() {
        assertEquals(limiter.getIncrement(), increment);
        assertEquals(limiter.getInterval(), interval);
        assertEquals(limiter.getMaxCapacity(), maxCapacity);
    }

    @Test
    public void checkInitialCapacity() {
        assertEquals(limiter.getCapacity(), 0);
    }

    /**
     * Test abilities of start and stop token supplying
     */
    @Test
    public void checkSupplyFunctional() {
        try {
            limiter.startSupply();
            TimeUnit.MILLISECONDS.sleep((long)Math.ceil((double)interval * 1.05));
            assertEquals(limiter.getCapacity(), increment);
            limiter.stopSupply();
            TimeUnit.MILLISECONDS.sleep(interval * 2);
            assertEquals(limiter.getCapacity(), increment);
            limiter.startSupply();
            TimeUnit.MILLISECONDS.sleep((long)Math.ceil((double)interval * 2.05));
            assertEquals(limiter.getCapacity(), increment * 3);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }

    private static class Flag {
        boolean flag = false;
        void setFlag(boolean flag) {
            this.flag = flag;
        }
        boolean value() {
            return flag;
        }
    }

    /**
     * Test ability of supplying tokens at a certain interval
     */
    @Test
    public void checkSupplyProcedure() {
        try {
            final Flag failFlag = new Flag();
            limiter.startSupply();
            Observable<Long> observable = Observable.interval(interval, TimeUnit.MILLISECONDS)
                    .map(v -> v + 1)
                    .map(v -> v * increment)
                    .takeUntil(v -> (v == maxCapacity || failFlag.value()));
            observable.subscribe(new DisposableObserver<Long>() {
                @Override
                public void onNext(Long v) {
                    try {
                        System.out.println("Checking capacity: " + v);
                        assertEquals(limiter.getCapacity(), (long) v);
                    }
                    catch (Error e) {
                        e.printStackTrace();
                        failFlag.setFlag(true);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                    failFlag.setFlag(true);
                }

                @Override
                public void onComplete() {}
            });
            Thread.sleep(fillTime * interval + 100);
            if (failFlag.value()) fail();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }

    /**
     * Test ability of rejecting new tokens when bucket is full
     */
    @Test
    public void checkBucketOverflow() {
        try {
            limiter.startSupply();
            waitUntilFull();
            assertEquals(limiter.getCapacity(), maxCapacity);
            TimeUnit.MILLISECONDS.sleep(interval * 3);
            assertEquals(limiter.getCapacity(), maxCapacity);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }

    /**
     * Test acquiring tokens which are less than <pre>maxCapacity</pre>
     */
    @Test
    public void checkBasicAcquisition() {
        try {
            limiter.startSupply();
            waitUntilFull();
            long shouldSleep = limiter.acquire(maxCapacity / 4);
            assertEquals(shouldSleep, 0);
            if (shouldSleep >= 0) {
                assertEquals(limiter.getCapacity(), maxCapacity - (maxCapacity / 4));
            }
            else {
                fail();
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }

    /**
     * Test acquiring tokens which are more than <pre>maxCapacity</pre>
     */
    @Test
    public void checkOverflowAcquisition() {
        try {
            limiter.startSupply();
            waitUntilFull();
            long shouldSleep = limiter.acquire(maxCapacity * 2);
            assertEquals(shouldSleep, fillTime * interval);
            assertEquals(limiter.getCapacity(), 0);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }

    /**
     * Test acquiring 2 sets of tokens which are both less than <pre>maxCapacity</pre>
     */
    @Test
    public void checkTwoAcquisitions() {
        try {
            limiter.startSupply();
            waitUntilFull();
            assertEquals(limiter.acquire(maxCapacity / 4), 0);
            assertEquals(limiter.acquire(maxCapacity / 4), 0);
            assertEquals(limiter.getCapacity(), maxCapacity / 2);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }

    /**
     * Test acquiring two sets of tokens which are accumulated more than <pre>maxCapacity</pre>
     */
    @Test
    public void checkTwoOverflowAcquisitions() {
        try {
            limiter.startSupply();
            waitUntilFull();
            Runnable r = () -> {
                assertEquals(limiter.acquire(maxCapacity / 4), 0);
                limiter.stopSupply();
            };
            r.run();
            assertEquals(limiter.getCapacity(), maxCapacity - (maxCapacity / 4));
            Runnable r2 = () -> assertEquals(limiter.acquire(
                    maxCapacity),
                    interval * (long)(Math.ceil((double)(maxCapacity / 4) / increment)));
            r2.run();
            assertEquals(limiter.getCapacity(), -maxCapacity / 4);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
    }
}
