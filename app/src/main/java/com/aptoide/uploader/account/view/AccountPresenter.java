package com.aptoide.uploader.account.view;

import com.aptoide.uploader.view.Presenter;
import com.aptoide.uploader.view.View;
import com.aptoide.uploader.account.AptoideAccountManager;
import io.reactivex.Scheduler;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.exceptions.OnErrorNotImplementedException;

public class AccountPresenter implements Presenter {

  private final AccountView view;
  private final AptoideAccountManager accountManager;
  private final AccountNavigator accountNavigator;
  private final CompositeDisposable compositeDisposable;
  private final Scheduler viewScheduler;

  public AccountPresenter(AccountView view, AptoideAccountManager accountManager,
      AccountNavigator accountNavigator, CompositeDisposable compositeDisposable,
      Scheduler viewScheduler) {
    this.view = view;
    this.accountManager = accountManager;
    this.accountNavigator = accountNavigator;
    this.compositeDisposable = compositeDisposable;
    this.viewScheduler = viewScheduler;
  }

  @Override public void present() {

    compositeDisposable.add(view.getLifecycleEvent()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(__ -> view.getLoginEvent()
            .doOnNext(credentials -> view.showLoading(credentials.getUsername()))
            .flatMapSingle(credentials -> accountManager.login(credentials.getUsername(),
                credentials.getPassword()))
            .observeOn(viewScheduler)
            .doOnNext(account -> {
              view.hideLoading();
              if (account.hasStore()) {
                accountNavigator.navigateToMyAppsView();
              } else {
                accountNavigator.navigateToCreateStoreView();
              }
            })
            .doOnError(throwable -> {
              view.hideLoading();
              if (isInternetError(throwable)) {
                view.showNetworkError();
              } else {
                view.showCrendentialsError();
              }
            })
            .retry())
        .subscribe(__ -> {
        }, throwable -> {
          throw new OnErrorNotImplementedException(throwable);
        }));

    compositeDisposable.add(view.getLifecycleEvent()
        .filter(event -> event.equals(View.LifecycleEvent.DESTROY))
        .doOnNext(__ -> compositeDisposable.clear())
        .subscribe(lifecycleEvent -> {
        }, throwable -> {
          throw new OnErrorNotImplementedException(throwable);
        }));
  }

  private boolean isInternetError(Throwable throwable) {
    if (throwable instanceof IllegalStateException) {
      return false;
    }
    return true;
  }
}
