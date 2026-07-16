
/* global $$, Utils, Router */

'use strict';

(async function () {

    const pendingTopic = Utils.getAndEraseData('helpTopic');

    // Static reference content for topics that don't have a dedicated screen.
    // Each entry: title + HTML body shown in the themed help popup.
    const TOPICS = {
        layout: {
            title: 'Repository layout',
            body:
                '<p>By convention, a Subversion repository is organized into three top-level ' +
                'directories: <code>/trunk</code>, <code>/branches</code>, and <code>/tags</code>.</p>' +
                '<p><b>trunk</b> holds the main line of development — the always-current state ' +
                'of the project. <b>branches</b> holds parallel lines of work (features, ' +
                'releases, experiments), each a cheap server-side copy of trunk. <b>tags</b> ' +
                'holds named, frozen snapshots such as <code>/tags/v1.0</code>.</p>' +
                '<p>These are ordinary directories, not special SVN objects — the layout is a ' +
                'shared discipline that makes branching and releasing predictable across teams.</p>'
        },
        checkout: {
            title: 'Checkout & update',
            body:
                '<p>A <b>checkout</b> creates a local working copy of a path in the repository:</p>' +
                '<p><code>svn checkout &lt;repo-url&gt;/trunk my-project</code></p>' +
                '<p>Unlike a distributed clone, a checkout is scoped to whatever path you point ' +
                'at — you can pull just <code>/trunk</code>, a single branch, or even one ' +
                'subdirectory.</p>' +
                '<p>Once you have a working copy, <b>update</b> pulls the newest revisions from ' +
                'the central line into it:</p>' +
                '<p><code>svn update</code></p>' +
                '<p><span class="wordmark">svn<span class="dot"></span>hub</span> tracks how far each working copy has drifted from HEAD, so you can ' +
                'see who is behind — the number GitHub can’t show.</p>'
        },
        revisions: {
            title: 'Revisions r1..rN',
            body:
                '<p>Subversion assigns one <b>global revision number</b> to every commit. The ' +
                'first commit is <code>r1</code>, the next <code>r2</code>, and so on — a ' +
                'single, monotonically increasing counter for the whole repository.</p>' +
                '<p>A revision number therefore identifies the exact state of the <i>entire</i> ' +
                'repository at that point in time, not just one file. <code>rN</code> always ' +
                'refers to the latest commit, also called HEAD.</p>' +
                '<p>You can inspect or check out any historical revision, for example ' +
                '<code>svn update -r 42</code> to move a working copy back to <code>r42</code>.</p>'
        },
        locks: {
            title: 'Locks',
            body:
                '<p>Some files — images, binaries, office documents — cannot be sensibly merged ' +
                'when two people edit them at once. For these, Subversion offers <b>locking</b>.</p>' +
                '<p>A lock reserves a file so that only the lock holder can commit changes to ' +
                'it until the lock is released:</p>' +
                '<p><code>svn lock design.psd</code> &nbsp;·&nbsp; <code>svn unlock design.psd</code></p>' +
                '<p>This is the "lock-modify-unlock" model, in contrast to the ' +
                '"copy-modify-merge" model used for ordinary text files. Locks prevent the lost ' +
                'work that would otherwise happen when unmergeable files collide.</p>'
        },
        mr: {
            title: 'Merge requests',
            body:
                '<p>A <b>merge request</b> proposes bringing the changes on one path (a <i>source</i> — a branch, ' +
                'or the trunk of a fork) into another (a <i>target</i>, usually <code>/trunk</code>).</p>' +
                '<h4>Opening a request</h4>' +
                '<p>Give a <b>source path</b> and a <b>target path</b>. From a fork you can also choose ' +
                '<b>Merge into: Upstream</b> to propose your changes back to the repository you forked from. ' +
                'By default every eligible commit is included; cherry-pick specific source revisions with the ' +
                '<b>Commits to include</b> box — e.g. <code>5</code>, <code>3-7</code>, or <code>3-7,10,12-15</code>.</p>' +
                '<h4>Reviewing &amp; commenting</h4>' +
                '<p>Reviewers read the generated diff under <b>Changes</b>, add a general comment, or click a line ' +
                'number to leave an <i>inline</i> comment anchored to that file and line. Anyone who can see the ' +
                'repository can comment — write access is not required.</p>' +
                '<h4>Merging</h4>' +
                '<p>Someone with <b>write access to the target</b> clicks <b>Approve &amp; Merge</b>; SVNKit performs ' +
                'the merge and commits it, producing the next revision on the target. <b>Close request</b> declines ' +
                'it without merging. Because SVN branches are just directories, the merge is an ordinary server-side commit.</p>'
        },
        fork: {
            title: 'Forks',
            body:
                '<p>A <b>fork</b> is your own full-history copy of another repository, created under your account ' +
                'from its <b>Fork</b> button. You own the fork and have read/write on it, so you can commit freely ' +
                'without needing access to the original.</p>' +
                '<p>A fork is a <b>point-in-time snapshot</b> — later commits to the original do not flow into it ' +
                'automatically — and its visibility is inherited from the original.</p>' +
                '<p>To contribute your work back, commit to your fork, then open a merge request and choose ' +
                '<b>Merge into: Upstream</b>. The request lives in the original repository, where its owner (or ' +
                'anyone with write access there) reviews and merges it. Only the commits you select are merged.</p>'
        }
    };

    function openTopic(key) {
        const topic = TOPICS[key];
        if (!topic)
            return;
        $$('help-topic-title').setValue(topic.title);
        $$('help-topic-body').setHTMLValue(topic.body);
        Utils.popup_open('help-topic-popup');
    }

    $$('help-topic-close').onclick(() => Utils.popup_close());

    $$('about-subversion').onclick(function () {
        Router.go('/about-subversion');
    });

    $$('topic-layout').onclick(() => openTopic('layout'));
    $$('topic-checkout').onclick(() => openTopic('checkout'));
    $$('topic-revisions').onclick(() => openTopic('revisions'));
    $$('topic-locks').onclick(() => openTopic('locks'));
    $$('topic-mr').onclick(() => openTopic('mr'));
    $$('topic-fork').onclick(() => openTopic('fork'));

    if (Utils.setAppNavActive)
        Utils.setAppNavActive('help');

    if (pendingTopic && TOPICS[pendingTopic])
        window.setTimeout(() => openTopic(pendingTopic), 0);

})();
