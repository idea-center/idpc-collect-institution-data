package org.ideaedu.data.report

import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

import groovy.json.JsonOutput

/**
 * The Main class provides a way to pull all the data for an institution starting on/after a specific date
 * and ending on/before a specific date. This will write the data to files in the given directory. The data
 * is pulled from the IDEA Data Portal using the given credentials. It has some optional command line arguments
 * that control the behavior. The arguments include:
 * <ul>
 * <li>h (host) - the hostname of the IDEA REST Server</li>
 * <li>p (port) - the port that is open on the IDEA REST Server</li>
 * <li>b (basePath) - the base path within the IDEA REST Server</li>
 * <li>iid (institutionID) - the institution ID to use for this survey (which institution this survey is associated with)</li>
 * <li>v (verbose) - provide more output on the command line</li>
 * <li>a (app) - the client application name</li>
 * <li>k (key) - the client application key</li>
 * <li>s (ssl) - use SSL/TLS for connection to the IDEA REST Server</li>
 * <li>st (start) - the date the surveys must start after (or on).</li>
 * <li>en (end) - the date the surveys must end before (or on).</li>
 * <li>d (directory) - the directory to create and write the data to.</li>
 * <li>? (help) - show the usage of this</li>
 * </ul>
 *
 * @author Todd Wallentine todd AT IDEAedu org
 */
public class Main {

    private static final int MAX_SURVEYS_PER_PAGE = 100

    private static final int DEFAULT_INSTITUTION_ID = 3019 // ID_INSTITUTION in Combo for The IDEA Center
    private static final String DEFAULT_HOSTNAME = 'localhost'
    private static final int DEFAULT_PORT = 8091
    private static final String DEFAULT_BASE_PATH = 'IDEA-REST-SERVER/v1/'
    private static final def DEFAULT_AUTH_HEADERS = [ 'X-IDEA-APPNAME': '', 'X-IDEA-KEY': '' ]
    private static final String DEFAULT_PROTOCOL = 'http'

    private static def protocol = DEFAULT_PROTOCOL
    private static String hostname = DEFAULT_HOSTNAME
    private static int port = DEFAULT_PORT
    private static String basePath = DEFAULT_BASE_PATH
    private static int institutionID = DEFAULT_INSTITUTION_ID
    private static def authHeaders = DEFAULT_AUTH_HEADERS
    private static String startDate // TODO Set a default
    private static String endDate // TODO Set a default
    private static File outputDirectory // TODO Set a default

    private static boolean verboseOutput = false

    private static RESTClient restClient

    static void main(String[] args) {

        def cli = new CliBuilder( usage: 'Main -v -s -h host -p port -b basePath -iid instID -a "TestClient" -k "ABCDEFG123456" -st "2017-01-01" -en "2017-12-30" -d myData' )
        cli.with {
            v longOpt: 'verbose', 'verbose output'
            s longOpt: 'ssl', 'use SSL (default: false)'
            h longOpt: 'host', 'host name (default: localhost)', args: 1
            p longOpt: 'port', 'port number (default: 8091)', args: 1
            b longOpt: 'basePath', 'base REST path (default: IDEA-REST-SERVER/v1/', args: 1
            iid longOpt: 'institutionID', 'institution ID', args: 1
            a longOpt: 'app', 'client application name', args: 1
            k longOpt: 'key', 'client application key', args: 1
            st longOpt: 'start', 'start date', args: 1
            en longOpt: 'end', 'end date', args: 1
            d longOpt: 'directory', 'output directory', args: 1
            '?' longOpt: 'help', 'help'
        }
        def options = cli.parse(args)
        if(options.'?') {
            cli.usage()
            return
        }
        if(options.v) {
            verboseOutput = true
        }
        if(options.s) {
            protocol = 'https'
        }
        if(options.h) {
            hostname = options.h
        }
        if(options.p) {
            port = options.p.toInteger()
        }
        if(options.b) {
            basePath = options.b
        }
        if(options.iid) {
            institutionID = options.iid.toInteger()
        }
        if(options.a) {
            authHeaders['X-IDEA-APPNAME'] = options.a
        }
        if(options.k) {
            authHeaders['X-IDEA-KEY'] = options.k
        }
        if(options.d) {
            outputDirectory = new File(options.d)
            outputDirectory.mkdirs()
        }
        if(options.st) {
            startDate = options.st
        }
        if(options.en) {
            endDate = options.en
        }

         // Create the root directory for this institution using the base output directory and the institution ID
         def institutionDirectory = new File(outputDirectory, getSafeDirectoryName(institutionID as String))
         institutionDirectory.mkdir()

         def page = 0
         def currentSurveyCount = 0
         def surveysPage = getSurveys(page, MAX_SURVEYS_PER_PAGE, institutionID, startDate, endDate, '19,21')
         while((surveysPage?.data?.size() > 0) && (surveysPage?.total_results > currentSurveyCount)) {
            surveysPage.data.each { survey ->
                if(survey) {
                    def surveyID = survey.id
                    def sourceSurveyID = survey.src_id

                    // Create the survey directory using the source survey ID
                    def surveyDirectory = new File(institutionDirectory, getSafeDirectoryName(sourceSurveyID))
                    surveyDirectory.mkdir()

                    def report = getReport(surveyID, getReportType(survey.rater_form.id))
                    if(report) {
                        def reportID = report.id
                        def reportModel = getReportModel(reportID)
                        if(reportModel) {
                            // Clean up extra data from JSON (_id, appId, reportId, updated_at)
                            reportModel.remove('_id')
                            reportModel.remove('appId')
                            reportModel.remove('reportId')
                            reportModel.remove('updated_at')

                            // Create the JSON file for the report model data using a static name (model.json)
                            def reportModelFile = new File(surveyDirectory, 'model.json')
                            reportModelFile.createNewFile()
                            reportModelFile << JsonOutput.prettyPrint(JsonOutput.toJson(reportModel))

                            // For each response data point, get the response data and dump to a file using the question ID in the name
                            reportModel.aggregate_data.response_data_points.each { responseDataPoint ->
                                if(responseDataPoint) {
                                    def questionID = responseDataPoint.question_id
                                    def responseData = getResponseData(reportID, questionID)
                                    if(responseData) {
                                        def responseDataFile = new File(surveyDirectory, "${questionID}.json")
                                        responseDataFile.createNewFile()
                                        responseDataFile << JsonOutput.prettyPrint(JsonOutput.toJson(responseData))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // update the count of how many surveys we have already pulled
            currentSurveyCount += surveysPage.data.size()

            page++ // get the next page
            surveysPage = getSurveys(page, MAX_SURVEYS_PER_PAGE, institutionID, startDate, endDate, '19,21')
         }
    }

    private static getResponseData(reportID, questionID) {
        def responseData

        if(reportID && questionID) {
            def client = getRESTClient()
            def response = client.get(
                path: "${basePath}/report/${reportID}/model/${questionID}",
                requestContentType: ContentType.JSON,
                headers: authHeaders)
            if(response.status == 200) {
                if(verboseOutput) {
                    println "Response data: ${response.data}"
                }
                responseData = response.data
            } else {
                println "An error occured while getting the response data for report ${reportID} and question ${questionID}: ${response.status}"
                println "${response.data}"
            }
        }

        return responseData
    }

    private static getReportModel(reportID) {
        def reportModel

        if(reportID) {
            def client = getRESTClient()
            def response = client.get(
                path: "${basePath}/report/${reportID}/model",
                requestContentType: ContentType.JSON,
                headers: authHeaders)
            if(response.status == 200) {
                if(verboseOutput) {
                    println "Report Model data: ${response.data}"
                }
                reportModel = response.data
            } else {
                println "An error occured while getting the report model for report ${reportID}: ${response.status}"
                println "${response.data}"
            }
        }

        return reportModel
    }

    private static getReport(surveyID, reportType) {
        def report

        if(surveyID) {
            def client = getRESTClient()
            def response = client.get(
                path: "${basePath}/reports",
                query: [ survey_id: surveyID, type: reportType ],
                requestContentType: ContentType.JSON,
                headers: authHeaders)
            if(response.status == 200) {
                if(verboseOutput) {
                    println "Reports data: ${response.data}"
                }
                def reports = response.data
                report = reports.data[0]
            } else {
                println "An error occured while getting the reports for survey ${surveyID}, ${reportType}: ${response.status}"
                println "${response.data}"
            }
        }

        return report
    }

    private static getReportType(formTypeID) {
        def reportType = ''

        switch(formTypeID) {
            case 20:
                reportType = 'Teaching Essentials'
            break
            case 22:
                reportType = 'Diagnostic'
            break
            case 23:
                reportType = 'Learning Essentials'
            break
        }

        return reportType
    }

    /**
     * Create a name that will be a valid directory name. This will
     * <ol>
     * <li>Replace all periods with underscores</li>
     * <li>Remove all special characters (exception of spaces, underscores, and dashes)</li>
     * <li>Replace all spaces with underscores</li>
     * </ol>
     *
     * @param originalName The original name of the directory.
     * @return A safe name for a directory.
     */
    private static getSafeDirectoryName(originalName) {
        def safeName = ''

        if(originalName) {
            // Replace spaces with underscores
            // Remove special characters (slashs, comma, period, ampersand, at, exclamation point, hash, dollar, percent, carat, star, parens, equals, pipe, ...)
            safeName = originalName.replaceAll('\\.', '_').replaceAll('[^A-Za-z0-9 _-]', '').replaceAll('[ ]', '_')
        }

        return safeName
    }

    private static getSafeFileName(originalName, extension) {
        def safeName = ''

        if(originalName) {
            safeName = originalName.replaceAll('\\.', '_').replaceAll('[^A-Za-z0-9 _-]', '').replaceAll('[ ]', '_')

            if(extension) {
                safeName += ".${extension}"
            }
        }

        return safeName
    }

    private static getSurveys(page, max, institutionID, startDate, endDate, types) {
        def surveys = [:]

        if(page >= 0 && max > 0 && institutionID && startDate && endDate) {
            def client = getRESTClient()
            def response = client.get(
                path: "${basePath}/surveys",
                query: [ page: page, max: max, institution_id: institutionID, start_date: startDate, end_date: endDate, types: types ],
                requestContentType: ContentType.JSON,
                headers: authHeaders)
            if(response.status == 200) {
                if(verboseOutput) {
                    println "Surveys data: ${response.data}"
                }
                surveys = response.data
            } else {
                println "An error occured while getting the surveys ${institutionID}, ${startDate}, ${endDate}: ${response.status}"
                println "${response.data}"
            }
        }

        return surveys
    }


    /**
     * Get an instance of the RESTClient that can be used to access the REST API.
     *
     * @return RESTClient An instance that can be used to access the REST API.
     */
    private static getRESTClient() {
        if(restClient == null) {
            if(verboseOutput) {
                println "REST requests will be sent to ${hostname} on port ${port} (protocol: ${protocol})"
            }
            restClient = new RESTClient("${protocol}://${hostname}:${port}/")
            restClient.ignoreSSLIssues()
            restClient.handler.failure = { response ->
                if(verboseOutput) {
                    println "The REST call failed: ${response.status}"
                }
                return response
            }
        }

        return restClient
    }
}