
/* global $$, Utils, Server */

'use strict';

(async function () {

    const screenArea = 'app-screen-area';

    $$('repositories').onclick(function () {
        Utils.cleanup();
        Utils.loadPage('screens/Repositories/Repositories', screenArea);
    });

    $$('discover').onclick(function () {
        Utils.cleanup();
        Utils.loadPage('screens/Discover/Discover', screenArea);
    });

    $$('insights').onclick(function () {
        Utils.cleanup();
        Utils.loadPage('screens/Insights/Insights', screenArea);
    });

    $$('users').onclick(function () {
        Utils.cleanup();
        Utils.loadPage('screens/Users/Users', screenArea);
    });

    $$('logout').onclick(function () {
        Server.logout();
    });

    // User administration is admin-only; hide it for regular users.
    if (Utils.getData('isAdmin') !== true)
        $$('users').hide();

    // Land on the repository list by default.
    Utils.cleanup();
    Utils.loadPage('screens/Repositories/Repositories', screenArea);

})();
