import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by LLAP on 2019/8/5.
 * Copyright (c) 2019 LLAP. All rights reserved.
 */
public class RateLimiter {

    public boolean debug = true;
    private final AtomicLong bucket;
    private final Observable<Long> supplier;
    private Disposable subscriber;
    private final long interval;
    private final long increment;
    private final long maxCapacity;

    /**
     *
     * @param interval 指定间隔多久补充一次令牌
     * @param increment 每次补充多少令牌
     * @param maxCapacity 桶的最大容量
     */
    public RateLimiter(long interval, long increment, long maxCapacity) {
        this.interval = interval;
        this.increment = increment;
        this.maxCapacity = maxCapacity;
        System.out.println("Bucket initialized");
        bucket = new AtomicLong(0);
        supplier = Observable.interval(this.interval, TimeUnit.MILLISECONDS).map(v -> increment);
        startSupply();
    }

    public long getInterval() {
        return interval;
    }

    public long getIncrement() {
        return increment;
    }

    public long getMaxCapacity() {
        return maxCapacity;
    }

    public long getCapacity() {
        return bucket.get();
    }

    private boolean checkPermits(long arg) {
        return arg > 0;
    }

    /**
     * 获取指定数量的令牌
     * 若桶中令牌数量不足，则将所有令牌扣除，并为此请求阻塞至补充到足够数量的令牌
     * @param count 令牌数量
     * @return 需要阻塞的毫秒数（不需要阻塞返回零，获取失败返回-1）
     * @throws IllegalArgumentException count参数小于等于零
     */
    public long acquire(long count) throws IllegalArgumentException {
        System.out.println(String.format("Acquiring %d tokens", count));
        if (!checkPermits(count)) throw new IllegalArgumentException("Acquirement must > 0");
        long shouldSleep = preserve(count);
        if (shouldSleep > 0) {
            try {
                System.out.println(String.format("Sleep %d ms", shouldSleep));
                TimeUnit.MILLISECONDS.sleep(shouldSleep);
            }
            catch (InterruptedException ex) {
                ex.printStackTrace();
                return -1;
            }
        }
        System.out.println(String.format("%d tokens acquired", count));
        printBucket();
        return shouldSleep;
    }

    /**
     * 从桶中扣除相应数量的令牌
     * @param count 扣除的令牌数量
     * @return 要阻塞的毫秒数
     */
    private long preserve(long count) {
        synchronized (bucket) {
            System.out.println(String.format("Bucket = %d", bucket.get()));
            long ret = 0;
            long bucketHas = bucket.addAndGet(-count);
            if (bucketHas < 0) {
                ret = (long)Math.ceil((double)-bucketHas / increment) * interval;
            }
            System.out.println(String.format("Bucket - %d = %d", count, bucket.get()));
            return ret;
        }
    }

    /**
     * 开始定时补充令牌
     */
    public void startSupply() {
        System.out.println("Starting supplier");
        if (this.subscriber == null || this.subscriber.isDisposed()) {
            this.subscriber = supplier.subscribe(emitter -> {
                supply(emitter);
                printBucket();
            });
            System.out.println("Supplier started");
            printBucket();
        }
        else {
            System.out.println("Supplier already started");
        }
    }

    private void printBucket() {
        if (debug) System.out.println(String.format("%d Bucket = %d", System.currentTimeMillis(), bucket.get()));
    }

    /**
     * 停止定时补充令牌
     */
    public void stopSupply() {
        if (subscriber != null && !subscriber.isDisposed()) {
            subscriber.dispose();
            System.out.println("Supplier stopped");
        }
        else System.out.println("Supplier already stopped");
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        stopSupply();
    }

    /**
     * 补充令牌
     * @param increment 补充的数量
     * @return 桶中剩余的令牌数
     */
    private long supply(long increment) {
        synchronized (bucket) {
            long bucketHas = bucket.get();
            if (bucketHas < 0) {
                long preservedDelta = Math.min(-bucket.get(), increment);
                increment -= preservedDelta;
                if (preservedDelta > 0) bucket.addAndGet(preservedDelta);
            }
            long delta = Math.min(maxCapacity - bucket.get(), increment);
            if (delta > 0) return bucket.addAndGet(delta);
            return maxCapacity;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            RateLimiter limiter = new RateLimiter(1000, 20, 100);
            TimeUnit.SECONDS.sleep(5);
            limiter.acquire(35);
//            TimeUnit.SECONDS.sleep(1);
            limiter.acquire(100);
            limiter.acquire(50);
            TimeUnit.SECONDS.sleep(9);
            limiter.stopSupply();
        }
        catch (IllegalArgumentException argErr) {
            argErr.printStackTrace();
        }
    }
}
