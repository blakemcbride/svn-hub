/* global $$, Utils, Server, AGGrid, DateTimeUtils */
'use strict';

(async function () {

    const WS = 'services/DiscoverService';

    function fmtDate(ms) {
        if (!ms)
            return '';
        try {
            return DateTimeUtils.formatDate(ms);
        } catch (e) {
            return '' + ms;
        }
    }

    // ---- people directory ----
    const peopleGrid = new AGGrid('people-grid', [
        {headerName: 'Username', field: 'handle', flex: 1},
        {headerName: 'Name', field: 'fullName', flex: 2},
        {headerName: 'Public repos', field: 'publicRepoCount', width: 130}
    ], 'handle');
    peopleGrid.show();

    async function searchPeople() {
        peopleGrid.clear();
        const res = await Server.call(WS, 'searchUsers', {query: $$('disc-search').getValue()});
        if (res._Success)
            peopleGrid.addRecords(res.rows);
    }
    $$('disc-search-go').onclick(searchPeople);
    $$('disc-search').onEnter(searchPeople);
    peopleGrid.setOnRowDoubleClicked(() => {
        const row = peopleGrid.getSelectedRow();
        if (row)
            loadProfile(row.handle);
    });

    // ---- selected person's repositories ----
    const repoGrid = new AGGrid('profile-repos-grid', [
        {headerName: 'Name', field: 'name', flex: 2},
        {headerName: 'Visibility', field: 'visibility', width: 110},
        {headerName: 'Description', field: 'description', flex: 3},
        {headerName: 'HEAD', field: 'headRevision', width: 80},
        {headerName: 'Checkout URL', field: 'checkoutUrl', flex: 3}
    ], 'repoId');
    repoGrid.show();
    repoGrid.setOnRowDoubleClicked(() => {
        const row = repoGrid.getSelectedRow();
        if (!row)
            return;
        Utils.saveData('repoId', row.repoId);
        Utils.saveData('repoKey', row.repoKey);
        Utils.saveData('repoName', row.name);
        Utils.cleanup();
        Utils.loadPage('screens/Repository/Repository', 'app-screen-area');
    });

    async function loadProfile(handle) {
        const res = await Server.call(WS, 'getProfile', {handle: handle});
        if (!res._Success)
            return;
        const p = res.profile;
        $$('disc-profile-title').setValue('@' + p.handle + (p.fullName ? ' — ' + p.fullName : ''));
        $$('disc-profile-sub').setValue(
            (p.memberSince ? 'Member since ' + fmtDate(p.memberSince) + '  ·  ' : '') +
            res.repos.length + ' repositor' + (res.repos.length === 1 ? 'y' : 'ies') +
            ' (double-click a repository to open it)');
        repoGrid.clear();
        repoGrid.addRecords(res.repos);
    }

    await searchPeople();   // show everyone initially

})();
