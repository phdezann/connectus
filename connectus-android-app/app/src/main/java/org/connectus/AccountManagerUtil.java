package org.connectus;

import android.accounts.Account;
import android.accounts.AccountManager;
import rx.Observable;

import javax.inject.Inject;
import java.util.NoSuchElementException;

public class AccountManagerUtil {

    public static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    @Inject
    AccountManager accountManager;

    @Inject
    public AccountManagerUtil() {
    }

    public Observable<Account> findAccount(String email) {
        return Observable.create(s -> {
            for (Account account : accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE)) {
                if (account.name.equals(email)) {
                    s.onNext(account);
                    s.onCompleted();
                    return;
                }
            }
            s.onError(new NoSuchElementException());
        });
    }
}
