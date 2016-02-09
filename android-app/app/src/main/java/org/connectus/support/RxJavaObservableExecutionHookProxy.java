package org.connectus.support;

import lombok.Setter;
import rx.Observable;
import rx.Subscription;
import rx.plugins.RxJavaObservableExecutionHook;

public class RxJavaObservableExecutionHookProxy extends RxJavaObservableExecutionHook {

    @Setter
    public static RxJavaObservableExecutionHook target = new RxJavaObservableExecutionHook() {};

    @Override
    public <T> Observable.OnSubscribe<T> onCreate(Observable.OnSubscribe<T> f) {
        return target.onCreate(f);
    }

    @Override
    public <T> Observable.OnSubscribe<T> onSubscribeStart(Observable<? extends T> observableInstance, final Observable.OnSubscribe<T> onSubscribe) {
        return target.onSubscribeStart(observableInstance, onSubscribe);
    }

    @Override
    public <T> Subscription onSubscribeReturn(Subscription subscription) {
        return target.onSubscribeReturn(subscription);
    }

    @Override
    public <T> Throwable onSubscribeError(Throwable e) {
        return target.onSubscribeError(e);
    }

    @Override
    public <T, R> Observable.Operator<? extends R, ? super T> onLift(final Observable.Operator<? extends R, ? super T> lift) {
        return target.onLift(lift);
    }
}
