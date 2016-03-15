package org.connectus.support;

import rx.Observable;
import rx.functions.Func1;

/**
 * NoOp refers to a special object and class that represent a value with no interest. This can useful for using observables
 * that output no item and taking advantage of the expressiveness of {@link Observable#flatMap(Func1)}.
 */
public class NoOpObservable {

    private static NoOp noOp = new NoOp();

    public static class NoOp {}

    public static Observable<NoOp> justNoOp() {
        return Observable.just(noOp);
    }

    public static NoOp noOp() {
        return noOp;
    }
}
