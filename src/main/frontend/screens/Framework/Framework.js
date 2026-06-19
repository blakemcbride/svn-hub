
/* global $$, Utils, Server */

'use strict';

(async function () {

    const screenArea = 'app-screen-area';

    $$('repositories').onclick(function () {
        Utils.cleanup();
        Utils.loadPage('screens/Repositories/Repositories', screenArea);
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

    // Land on the repository list by default.
    Utils.cleanup();
    Utils.loadPage('screens/Repositories/Repositories', screenArea);

})();
