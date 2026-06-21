
/* global $$, Server, Utils */

'use strict';

(function () {

    const WS = 'services/PasswordResetService';

    async function sendCode() {
        if ($$('email').isError('Email'))
            return;
        const email = $$('email').getValue().trim().toLowerCase();
        const res = await Server.call(WS, 'requestReset', {email: email});
        if (res._Success) {
            // The server always reports success (it never reveals whether the
            // account exists), so the message is deliberately conditional.
            $$('sent-msg').setValue('If an account exists for that email, a code has been sent. Enter it below.');
            $$('code').focus();
        }
    }

    async function reset() {
        if ($$('email').isError('Email'))
            return;
        if ($$('code').isError('Code'))
            return;
        if ($$('password').isError('New password'))
            return;
        if ($$('password2').isError('Confirm'))
            return;
        const password = $$('password').getValue();
        if (password.length < 6) {
            Utils.showMessage('Error', 'Your new password must be at least 6 characters.');
            return;
        }
        if (password !== $$('password2').getValue()) {
            Utils.showMessage('Error', 'The passwords do not match.');
            return;
        }
        const res = await Server.call(WS, 'resetPassword', {
            email: $$('email').getValue().trim().toLowerCase(),
            code: $$('code').getValue().trim(),
            newPassword: password
        });
        if (res._Success) {
            await Utils.showMessage('Password reset', 'Your password has been changed. Please sign in.');
            Utils.loadPage('login');
        }
    }

    $$('send-code').onclick(sendCode);
    $$('reset').onclick(reset);
    $$('password2').onEnter(reset);
    $$('to-login').onclick(function () {
        Utils.loadPage('login');
    });

    $$('email').focus();

})();
