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
    peopleGrid.setOnRowDoubleClicked(() => {
        const row = peopleGrid.getSelectedRow();
        if (row)
            loadProfile(row.handle);
    });

    // ---- projects (search matches, or one person's repos after drilling in) ----
    const repoGrid = new AGGrid('repos-grid', [
        {headerName: 'Owner', field: 'ownerHandle', width: 130},
        {headerName: 'Name', field: 'name', flex: 2},
        {headerName: 'Visibility', field: 'visibility', width: 100},
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

    // One search box drives both people and project results.
    async function search() {
        const query = $$('disc-search').getValue();

        peopleGrid.clear();
        const people = await Server.call(WS, 'searchUsers', {query: query});
        if (people._Success)
            peopleGrid.addRecords(people.rows);

        repoGrid.clear();
        $$('disc-repos-title').setValue('Projects');
        const repos = await Server.call(WS, 'searchRepos', {query: query});
        if (repos._Success) {
            repoGrid.addRecords(repos.rows);
            $$('disc-repos-sub').setValue(repos.rows.length +
                ' matching project' + (repos.rows.length === 1 ? '' : 's') +
                ' (double-click a project to open it)');
        }
    }
    $$('disc-search-go').onclick(search);
    $$('disc-search').onEnter(search);

    // Drill into one person: show all of their repos in the projects grid.
    async function loadProfile(handle) {
        const res = await Server.call(WS, 'getProfile', {handle: handle});
        if (!res._Success)
            return;
        const p = res.profile;
        repoGrid.clear();
        repoGrid.addRecords(res.repos);
        $$('disc-repos-title').setValue('@' + p.handle + (p.fullName ? ' — ' + p.fullName : ''));
        $$('disc-repos-sub').setValue(
            (p.memberSince ? 'Member since ' + fmtDate(p.memberSince) + '  ·  ' : '') +
            res.repos.length + ' repositor' + (res.repos.length === 1 ? 'y' : 'ies') +
            ' (double-click a project to open it)');
    }

    await search();   // show everyone / all public projects initially

})();
