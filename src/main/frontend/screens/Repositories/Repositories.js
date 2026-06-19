/* global $$, Utils, Server, AGGrid */
'use strict';

(async function () {

    const WS_REPO = 'services/RepositoryService';
    const WS_ACC = 'services/RepositoryAccessService';

    let isAdmin = false;
    let currentRepo = null;
    let accessRepoId = null;
    let accSelected = null;

    // ---- repository list ----
    const repoCols = [
        {headerName: 'Name', field: 'name', flex: 2},
        {headerName: 'Key', field: 'repoKey', flex: 1},
        {headerName: 'Description', field: 'description', flex: 3},
        {headerName: 'HEAD', field: 'headRevision', width: 90}
    ];
    const repoGrid = new AGGrid('repo-grid', repoCols, 'repoId');
    repoGrid.show();

    async function loadRepos() {
        repoGrid.clear();
        $$('repo-access').disable();
        const res = await Server.call(WS_REPO, 'getRepositories');
        if (res._Success) {
            isAdmin = res.isAdmin;
            repoGrid.addRecords(res.rows);
        }
    }

    repoGrid.setOnSelectionChanged((rows) => {
        currentRepo = repoGrid.getSelectedRow();
        $$('repo-access').enable(rows && isAdmin);
    });
    repoGrid.setOnRowDoubleClicked(openRepo);

    function openRepo() {
        const row = repoGrid.getSelectedRow();
        if (!row)
            return;
        Utils.saveData('repoId', row.repoId);
        Utils.saveData('repoKey', row.repoKey);
        Utils.saveData('repoName', row.name);
        Utils.cleanup();
        Utils.loadPage('screens/Repository/Repository', 'app-screen-area');
    }
    $$('repo-open').onclick(openRepo);

    // ---- create repository ----
    $$('repo-new').onclick(() => {
        $$('nr-key').clear();
        $$('nr-name').clear();
        $$('nr-desc').clear();
        $$('nr-layout').setValue(true);
        Utils.popup_open('new-repo-popup', 'nr-key');
    });
    $$('nr-cancel').onclick(() => Utils.popup_close());
    $$('nr-ok').onclick(async () => {
        if ($$('nr-key').isError('Key'))
            return;
        const data = {
            repoKey: $$('nr-key').getValue().trim(),
            name: $$('nr-name').getValue().trim(),
            description: $$('nr-desc').getValue().trim(),
            standardLayout: $$('nr-layout').getValue()
        };
        const res = await Server.call(WS_REPO, 'createRepository', data);
        if (res._Success) {
            Utils.popup_close();
            await loadRepos();
        }
    });

    // ---- scan disk ----
    $$('repo-scan').onclick(async () => {
        const res = await Server.call(WS_REPO, 'scanRepositories');
        if (res._Success) {
            Utils.showMessage('Scan complete', 'Added ' + res.added + ' repository(ies).');
            await loadRepos();
        }
    });

    // ---- access management ----
    const accCols = [
        {headerName: 'User', field: 'userName', flex: 2},
        {headerName: 'Read', field: 'canRead', width: 75},
        {headerName: 'Write', field: 'canWrite', width: 80},
        {headerName: 'Admin', field: 'canAdmin', width: 80},
        {headerName: 'SVN pw', field: 'hasSvnPassword', width: 90}
    ];
    const accGrid = new AGGrid('access-grid', accCols, 'userId');
    accGrid.show();

    accGrid.setOnSelectionChanged((rows) => {
        accSelected = accGrid.getSelectedRow();
        $$('acc-revoke').enable(rows);
        if (accSelected) {
            // Load the selected grant into the form so "Apply" updates it.
            $$('acc-user').setValue(String(accSelected.userId));
            $$('acc-read').setValue(accSelected.canRead === 'Y');
            $$('acc-write').setValue(accSelected.canWrite === 'Y');
            $$('acc-admin').setValue(accSelected.canAdmin === 'Y');
        }
    });

    async function loadAccess() {
        accGrid.clear();
        $$('acc-revoke').disable();
        const res = await Server.call(WS_ACC, 'getAccess', {repoId: accessRepoId});
        if (res._Success) {
            accGrid.addRecords(res.rows);
            $$('acc-user').clear();
            $$('acc-user').add('', '(select user)');
            for (const u of res.availableUsers)
                $$('acc-user').add(String(u.userId), u.userName + (u.fullName ? ' (' + u.fullName + ')' : ''));
        }
    }

    $$('repo-access').onclick(() => {
        if (!currentRepo)
            return;
        accessRepoId = currentRepo.repoId;
        $$('acc-title').setValue('Access — ' + currentRepo.name);
        $$('acc-read').setValue(true);
        $$('acc-write').setValue(false);
        $$('acc-admin').setValue(false);
        loadAccess();
        Utils.popup_open('access-popup');
    });
    $$('acc-close').onclick(() => Utils.popup_close());

    $$('acc-grant').onclick(async () => {
        const uid = $$('acc-user').getValue();
        if (!uid) {
            Utils.showMessage('Select a user', 'Choose a user to grant access to.');
            return;
        }
        const data = {
            repoId: accessRepoId,
            userId: parseInt(uid, 10),
            canRead: $$('acc-read').getValue(),
            canWrite: $$('acc-write').getValue(),
            canAdmin: $$('acc-admin').getValue()
        };
        const res = await Server.call(WS_ACC, 'grant', data);
        if (res._Success)
            await loadAccess();
    });

    $$('acc-revoke').onclick(() => {
        if (!accSelected)
            return;
        Utils.yesNo('Revoke', 'Remove ' + accSelected.userName + "'s access?", async () => {
            const res = await Server.call(WS_ACC, 'revoke', {repoId: accessRepoId, userId: accSelected.userId});
            if (res._Success)
                await loadAccess();
        });
    });

    await loadRepos();

})();
