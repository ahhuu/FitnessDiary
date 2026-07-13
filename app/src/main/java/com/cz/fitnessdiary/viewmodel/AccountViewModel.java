package com.cz.fitnessdiary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cz.fitnessdiary.model.AccountUser;
import com.cz.fitnessdiary.model.EmailCodeChallenge;
import com.cz.fitnessdiary.repository.AccountRepository;
import com.cz.fitnessdiary.utils.TextUtilsCompat;

public final class AccountViewModel extends AndroidViewModel {
    private final AccountRepository repository;
    private final MutableLiveData<AccountUser> account = new MutableLiveData<>();
    private final MutableLiveData<EmailCodeChallenge> challenge = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public AccountViewModel(@NonNull Application application) {
        super(application);
        repository = new AccountRepository(application);
        account.setValue(repository.getCurrentAccount());
    }

    public LiveData<AccountUser> getAccount() { return account; }
    public LiveData<EmailCodeChallenge> getChallenge() { return challenge; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getMessage() { return message; }
    public boolean isEmailVerified() { return repository.isEmailVerified(); }

    public void requestEmailCode(String email) {
        loading.setValue(true);
        repository.requestEmailCode(email, new AccountRepository.Callback<EmailCodeChallenge>() {
            @Override public void onSuccess(EmailCodeChallenge value) {
                loading.postValue(false);
                challenge.postValue(value);
                message.postValue("验证码已发送，请查收邮箱");
            }
            @Override public void onError(Throwable error) { fail(error); }
        });
    }

    public void verifyEmailCode(EmailCodeChallenge codeChallenge, String code) {
        loading.setValue(true);
        repository.verifyEmailCode(codeChallenge, code, new AccountRepository.Callback<AccountUser>() {
            @Override public void onSuccess(AccountUser value) {
                repository.bindCurrentLocalProfile(new AccountRepository.Callback<AccountUser>() {
                    @Override public void onSuccess(AccountUser boundAccount) {
                        finishLogin(boundAccount, true);
                    }

                    @Override public void onError(Throwable error) {
                        // Cloud login is valid even when this device has no local profile to bind.
                        finishLogin(value, false);
                    }
                });
            }
            @Override public void onError(Throwable error) { fail(error); }
        });
    }

    public void bindLocalProfile() {
        loading.setValue(true);
        repository.bindCurrentLocalProfile(new AccountRepository.Callback<AccountUser>() {
            @Override public void onSuccess(AccountUser value) {
                loading.postValue(false); account.postValue(value); message.postValue("已绑定当前本地档案");
            }
            @Override public void onError(Throwable error) { fail(error); }
        });
    }

    public void logout() { repository.logout(); account.setValue(null); message.setValue("已退出云端账号，本地记录仍保留"); }
    public void deleteAccount() { loading.setValue(true); repository.deleteAccount(new AccountRepository.Callback<Void>() {
        @Override public void onSuccess(Void value) { loading.postValue(false); account.postValue(null); }
        @Override public void onError(Throwable error) { fail(error); }
    }); }

    private void fail(Throwable error) {
        loading.postValue(false);
        String value = error.getMessage();
        message.postValue(TextUtilsCompat.isBlank(value) ? "操作失败，请稍后重试" : value);
    }

    private void finishLogin(AccountUser value, boolean bound) {
        loading.postValue(false);
        account.postValue(value);
        message.postValue(bound ? "登录成功，已绑定本地档案" : "登录成功，但未找到可绑定的本地档案");
    }
}
