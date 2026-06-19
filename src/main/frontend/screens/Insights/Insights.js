/* global $$, Utils, Server, AGGrid, DateTimeUtils */
'use strict';

(async function () {

    const WS_REPO = 'services/RepositoryService';
    const WS_STATS = 'services/StatsService';

    function fmtDate(ms) {
        if (!ms)
            return '';
        try {
            return DateTimeUtils.formatDate(ms);
        } catch (e) {
            return '' + ms;
        }
    }
    function esc(s) {
        return ('' + (s == null ? '' : s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    // Grids (created once; refreshed per repo).
    const freshGrid = new AGGrid('freshness-grid', [
        {headerName: 'User', field: 'username', flex: 1},
        {headerName: 'Synced rev', field: 'lastsyncedrevision', width: 110},
        {headerName: 'HEAD', field: 'headrevision', width: 90},
        {headerName: 'Behind', field: 'revisionsbehind', width: 90},
        {headerName: 'Last sync', field: 'lastSyncStr', flex: 1}
    ], 'username');
    freshGrid.show();

    const cvuGrid = new AGGrid('cvu-grid', [
        {headerName: 'User', field: 'username', flex: 1},
        {headerName: 'Checkouts', field: 'checkouts', width: 120},
        {headerName: 'Updates', field: 'updates', width: 120}
    ], 'username');
    cvuGrid.show();

    const hotGrid = new AGGrid('hot-grid', [
        {headerName: 'Path', field: 'path', flex: 1},
        {headerName: 'Hits', field: 'hits', width: 100}
    ], 'path');
    hotGrid.show();

    const staleGrid = new AGGrid('stale-grid', [
        {headerName: 'User', field: 'username', flex: 1},
        {headerName: 'Last activity', field: 'lastStr', flex: 1},
        {headerName: 'Behind', field: 'revisionsbehind', width: 90}
    ], 'username');
    staleGrid.show();

    function renderSummary(s) {
        const cats = s.byCategory || {};
        const max = Math.max(1, cats.checkout || 0, cats.update || 0, cats.switch || 0,
            cats.browse || 0, cats.commit || 0, cats.other || 0);
        function bar(label, val) {
            const w = Math.round(((val || 0) / max) * 220);
            return '<div class="ins-bar-row"><span class="ins-bar-label">' + label + '</span>' +
                '<span class="ins-bar" style="width:' + w + 'px"></span>' +
                '<span class="ins-bar-val">' + (val || 0) + '</span></div>';
        }
        const html =
            '<div class="ins-cards">' +
            card(s.headRevision, 'HEAD revision') +
            card(s.totalEvents, 'Total operations') +
            card(s.distinctUsers, 'Distinct users') +
            card(s.distinctClients, 'Client hosts') +
            '</div>' +
            '<div style="margin-top:12px;">' +
            bar('checkout', cats.checkout) + bar('update', cats.update) + bar('switch', cats.switch) +
            bar('browse', cats.browse) + bar('commit', cats.commit) + bar('other', cats.other) +
            '</div>';
        $$('ins-summary').setHTMLValue(html);
    }
    function card(n, label) {
        return '<div class="ins-card"><div class="n">' + esc(n == null ? '-' : n) +
            '</div><div class="l">' + esc(label) + '</div></div>';
    }

    async function loadAll(repoId) {
        const summary = await Server.call(WS_STATS, 'repoSummary', {repoId: repoId});
        if (summary._Success)
            renderSummary(summary);

        const fr = await Server.call(WS_STATS, 'freshness', {repoId: repoId});
        freshGrid.clear();
        if (fr._Success)
            freshGrid.addRecords(fr.rows.map((r) => Object.assign({}, r, {lastSyncStr: fmtDate(r.lastsyncts)})));

        const cvu = await Server.call(WS_STATS, 'checkoutVsUpdate', {repoId: repoId});
        cvuGrid.clear();
        if (cvu._Success)
            cvuGrid.addRecords(cvu.rows);

        const hot = await Server.call(WS_STATS, 'hotPaths', {repoId: repoId, limit: 20});
        hotGrid.clear();
        if (hot._Success)
            hotGrid.addRecords(hot.rows);

        const stale = await Server.call(WS_STATS, 'staleWorkingCopies', {repoId: repoId, days: 14});
        staleGrid.clear();
        if (stale._Success)
            staleGrid.addRecords(stale.rows.map((r) => Object.assign({}, r, {lastStr: fmtDate(r.lastactivityts)})));
    }

    // Populate the repo selector.
    const repos = await Server.call(WS_REPO, 'getRepositories');
    $$('ins-repo').clear();
    if (repos._Success && repos.rows.length) {
        for (const r of repos.rows)
            $$('ins-repo').add(String(r.repoId), r.name);
        $$('ins-repo').onChange(() => {
            const id = $$('ins-repo').getValue();
            if (id)
                loadAll(parseInt(id, 10));
        });
        $$('ins-repo').setValue(String(repos.rows[0].repoId));
        await loadAll(repos.rows[0].repoId);
    } else {
        $$('ins-summary').setValue('No repositories available.');
    }

})();
