def call(verb, channel, branch_name, build_url, job_name, build_number)
{
    def color

    if (verb == 'started') {
        color = 'warning'
        icon = ':pending:'
    }
    else if (verb == 'completed') {
        color = 'good'
        icon = ':success:'
    }
    else {
        color = 'danger'
        icon = ':failure:'
    }

    blocks = [
        [
            "type": "header",
            "text": ["type": "plain_text", "text": "${icon} Deploy ${verb}"]
        ],
        [
            "type": "section",
            "text": ["type": "mrkdwn", "text": "Job: <${build_url}|${job_name} #${build_number}>\nThis build contains the following commits:"]
        ],
        ["type": "divider"]
    ]

    // Find the most recent merge commit and print its parents. Merge commits have 2 parent commits: the base and the head.
    def merge_hashes = sh(returnStdout: true, script:"git log --first-parent ${branch_name} --merges -n 1 --format='%P'").trim()
    def (base_commit, latest_commit) = merge_hashes.tokenize(' ')
    // List the author email and subject of all commits between the base (excluding) and the head (inclusive) of the latest merge commit
    def commit_list = sh(returnStdout: true, script:"git log ${latest_commit} --not ${base_commit} --format='%ae %H %s' --no-merges").trim()
    def remote_url = sh(returnStdout: true, script:"git config --get remote.origin.url").trim()
    remote_url = remote_url.replace('.git', '')

    // Turn the list of commits into slack block kit sections, tagging the authors
    def commits = commit_list.split("\n")

    def slack_block_limit = 50;
    def slack_blocks_per_commit = 3;
    def total_commits = commits.size();
    def total_commits_shown = total_commits;
    def total_commits_truncated = 0;
    // If the slack message would go over "slack_block_limit", cut it down
    if (blocks.size() + (total_commits * slack_blocks_per_commit) > slack_block_limit) {
        total_commits_shown = (int) Math.floor((slack_block_limit - blocks.size() - 1) / slack_blocks_per_commit);
        total_commits_truncated = total_commits - total_commits_shown;
        commits = Arrays.copyOfRange(commits, 0, total_commits_shown);
    }

    commits.each { commit ->
        println("commit string: " + commit);
        def row = commit.split(' ', 3)
        def author_email = row[0]
        def hash = row[1]
        def subject = row[2]
        def slack_user_id = slackUserIdFromEmail(author_email)

        blocks.push([
            "type": "section",
            "text": ["type": "mrkdwn","text": "*<${remote_url}/commit/${hash}|${subject}>*"]
        ])
        blocks.push([
            "type": "context",
            "elements": [["type": "mrkdwn","text": "By <@${slack_user_id}> <${author_email}>"]]
        ])
        blocks.push(["type": "divider"])
    }

    if (total_commits_truncated > 0) {
        blocks.push([
            "type": "section",
            "text": [
                "type": "mrkdwn",
                "text": "*This deploy includes ${total_commits_truncated} additional commits.* Try squashing your commits when possible."
            ]
        ])
    }

    slackSend(channel: channel, message: "Deploy ${verb}", blocks: blocks)
}
