package org.connectus;

import com.google.common.collect.Lists;
import org.connectus.support.RxJavaObservableExecutionHookProxy;
import rx.Observable;
import rx.Subscriber;
import rx.plugins.RxJavaObservableExecutionHook;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RxJavaBlockingHook extends RxJavaObservableExecutionHook {

    private AtomicInteger subscriptions = new AtomicInteger(0);
    List<AllSubscriptionCompletedListener> listeners = Lists.newArrayList();

    public static RxJavaBlockingHook setupHook() {
        System.setProperty("rxjava.plugin.RxJavaObservableExecutionHook.implementation", RxJavaObservableExecutionHookProxy.class.getName());
        RxJavaBlockingHook hook = new RxJavaBlockingHook();
        RxJavaObservableExecutionHookProxy.setTarget(hook);
        return hook;
    }

    public void waitUntilFinished() {
        waitUntilFinished(1);
    }

    public void waitUntilFinished(long timeoutInSeconds) {
        if (subscriptions.get() != 0) {
            try {
                synchronized (subscriptions) {
                    subscriptions.wait(timeoutInSeconds * 1000);
                }
            } catch (InterruptedException e) {
                // ignore it
            }
        }
    }

    public <T> Observable.OnSubscribe<T> onSubscribeStart(Observable<? extends T> observableInstance, final Observable.OnSubscribe<T> onSubscribe) {
        subscriptions.incrementAndGet();
        return (subscriber) -> {
            Subscriber<T> wrapper = new Subscriber<T>() {
                @Override
                public void onCompleted() {
                    subscriber.onCompleted();
                    onFinally();
                }

                @Override
                public void onError(Throwable e) {
                    subscriber.onError(e);
                    onFinally();
                }

                @Override
                public void onNext(T t) {
                    subscriber.onNext(t);
                }
            };
            onSubscribe.call(wrapper);
        };
    }

    public interface AllSubscriptionCompletedListener {
        void call();
    }

    public void addSubscriptionCompletedListener(AllSubscriptionCompletedListener listener) {
        this.listeners.add(listener);
    }

    private <T> void onFinally() {
        int activeSubscriptionCount = subscriptions.decrementAndGet();
        if (activeSubscriptionCount <= 0) {
            synchronized (subscriptions) {
                subscriptions.notify();
            }
            for (AllSubscriptionCompletedListener listener : listeners) {
                listener.call();
            }
        }
    }

    public int getCount() {
        return subscriptions.get();
    }
}
