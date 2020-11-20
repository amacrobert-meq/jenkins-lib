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
    def commit_list=sh(returnStdout: true, script:"git log ${latest_commit} --not ${base_commit} --format='%ae %H %s' --no-merges").trim()
    def remote_url = sh(returnStdout: true, script:"git config --get remote.origin.url").trim()
    remote_url = remote_url.replace('.git', '')

    // Turn the list of commits into slack block kit sections, tagging the authors
    def commits = commit_list.split("\n")

    commits.each { commit ->
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

    slackSend(channel: channel, blocks: blocks)
}
