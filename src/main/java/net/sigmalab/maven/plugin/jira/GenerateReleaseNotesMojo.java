package net.sigmalab.maven.plugin.jira;

import static java.text.MessageFormat.format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.apache.maven.plugin.logging.Log;

import com.atlassian.jira.rest.client.IssueRestClient;
import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.Issue;
import com.google.common.collect.Iterables;

/**
 * Goal that generates release notes based on a version in a JIRA project.
 * 
 * NOTE: REST API access must be enabled in your JIRA installation. Check JIRA
 * docs for more info.
 * 
 * @goal generate-release-notes
 * @phase deploy
 * 
 * @author George Gastaldi
 * @author dgrierso
 */
public class GenerateReleaseNotesMojo extends AbstractJiraMojo {
    final Log log = getLog();

    /**
     * JQL Template to generate release notes. Parameter 0 = Project Key
     * Parameter 1 = Fix version
     * 
     * @parameter default-value=
     *            "project = ''{0}'' AND status in (Resolved, Closed) AND fixVersion = ''{1}''"
     * @required
     */
    String jqlTemplate;

    /**
     * Template used on each issue found by JQL Template. Parameter 0 = Issue
     * Key Parameter 1 = Issue Summary
     * 
     * @parameter default-value="[{0}] {1}"
     * @required
     */
    String issueTemplate;

    /**
     * Max number of issues to return
     * 
     * @parameter default-value="100"
     * @required
     */
    int maxIssues;

    /**
     * Released Version
     * 
     * @parameter default-value="${project.version}"
     * @required
     */
    String releaseVersion;

    /**
     * Target file
     * 
     * @parameter default-value="${project.build.directory}/releaseNotes.txt"
     * @required
     */
    File targetFile;

    /**
     * Text to be appended BEFORE all issues details.
     * 
     * @parameter
     */
    String beforeText;

    /**
     * Text to be appended AFTER all issues details.
     * 
     * @parameter
     */
    String afterText;

    @Override
    public void doExecute(JiraRestClient jiraRestClient) throws Exception {
        Iterable<BasicIssue> issues = getIssues(jiraRestClient);
        log.info("Found " + Iterables.size(issues) + " issues.");

        output(jiraRestClient, issues);
    }

    /**
     * Recover issues from JIRA based on JQL Filter
     */

    Iterable<BasicIssue> getIssues(JiraRestClient restClient) {
        String jql = format(jqlTemplate, getJiraProjectKey(), releaseVersion);
        log.info("JQL: " + jql);

        return restClient.getSearchClient().searchJql(jql, maxIssues, 0).claim().getIssues();
    }

    /**
     * Writes issues to output
     * 
     * @param issues
     */
    void output(JiraRestClient restClient, Iterable<BasicIssue> issues) throws IOException {
        IssueRestClient issueClient = restClient.getIssueClient();

        if ( targetFile == null ) {
            log.warn("No targetFile specified. Ignoring");
            return;
        }

        if ( issues == null ) {
            log.warn("No issues found. File will not be generated.");
            return;
        }

        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile, true), "UTF8");
        PrintWriter ps = new PrintWriter(writer);

        try {
            if ( beforeText != null ) {
                ps.println(beforeText);
            }

            for ( BasicIssue basicIssue : issues ) {
                Issue fullIssue = issueClient.getIssue(basicIssue.getKey()).claim();
                String issueDesc = format(issueTemplate, basicIssue.getKey(), fullIssue.getSummary());

                ps.println(issueDesc);
            }

            if ( afterText != null ) {
                ps.println(afterText);
            }
        }
        finally {
            ps.flush();
            ps.close();
        }
    }
}
