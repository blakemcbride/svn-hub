
/* global $$, DOMUtils, Server, Utils */

'use strict';

(function () {

    async function doRegister() {
        if ($$('username').isError('Username'))
            return;
        if ($$('password').isError('Password'))
            return;
        if ($$('password2').isError('Confirm'))
            return;
        const username = $$('username').getValue().toLowerCase();
        const password = $$('password').getValue();
        if (password !== $$('password2').getValue()) {
            Utils.showMessage('Error', 'The passwords do not match.');
            return;
        }

        const res = await Server.call('services/Register', 'register', {
            username: username,
            password: password,
            fullName: $$('full-name').getValue(),
            email: $$('email').getValue()
        });
        if (!res._Success)
            return;   // the framework already showed the error

        // GitHub-style: log the new user straight in.
        const login = await Server.call('', 'Login', {username: username, password: password});
        if (login._Success) {
            Server.setUUID(login.uuid);
            Utils.saveData('isAdmin', login.isAdmin === true);
            DOMUtils.preventNavigation(true, function () {
                Utils.yesNo('Confirm', 'Are you sure you want to logout?', function () {
                    Server.logout();
                });
            });
            Utils.loadPage('screens/Framework/Framework');
        } else {
            Utils.loadPage('login');
        }
    }

    $$('register').onclick(doRegister);
    $$('password2').onEnter(doRegister);
    $$('to-login').onclick(function () {
        Utils.loadPage('login');
    });
    $$('username').focus();

})();
