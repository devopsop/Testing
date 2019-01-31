deliveryPipelineView('ADAMS BUILD') {
    pipelineInstances(4)
    columns(1)
    updateInterval(10)
    enableManualTriggers()
    showAvatars()
    showChangeLog()
    showDescription()
    showPromotions()
    showTestResults()
    showStaticAnalysisResults()
    showTotalBuildTime()
    useRelativeLinks()
    linkToConsoleLog()
    enablePaging()
    useTheme('Contrast')
    pipelines {
        component('ADAMS BUILD', 'ADAMS_BUILD_MERGED')
        component('ADAMS BUILD CGI', 'ADAMS_BUILD_CGI_MERGED')
    }
}
