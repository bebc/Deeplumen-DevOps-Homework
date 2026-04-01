def call(Map config = [:]) {
    validateConfig(config)

    String action = config.action
    String appName = config.appName
    String namespace = config.namespace
    String imageTag = config.imageTag
    String replicas = config.replicas ?: '2'
    String kubeconfigFile = config.kubeconfigFile
    String kubectlBin = config.kubectlBin ?: 'kubectl'

    switch (action) {
        case 'deploy':
            renderAndApply(appName, namespace, imageTag, replicas, kubeconfigFile, kubectlBin)
            checkRolloutWithRollback(appName, namespace, kubeconfigFile, kubectlBin)
            break
        case 'update':
            runKubectl(kubectlBin, kubeconfigFile, "set image deployment/${appName} ${appName}=${appName}:${imageTag} -n ${namespace}")
            checkRolloutWithRollback(appName, namespace, kubeconfigFile, kubectlBin)
            break
        case 'delete':
            runKubectl(kubectlBin, kubeconfigFile, "delete deployment ${appName} -n ${namespace} --ignore-not-found=true")
            runKubectl(kubectlBin, kubeconfigFile, "delete service ${appName} -n ${namespace} --ignore-not-found=true")
            runKubectl(kubectlBin, kubeconfigFile, "delete ingress ${appName} -n ${namespace} --ignore-not-found=true")
            break
        default:
            error("Unsupported ACTION: ${action}")
    }
}

def validateConfig(Map config) {
    ['action', 'appName', 'namespace', 'imageTag', 'kubeconfigFile'].each { key ->
        if (!config[key]) {
            error("Missing required config field: ${key}")
        }
    }

    if (!['deploy', 'update', 'delete'].contains(config.action)) {
        error("ACTION must be one of deploy|update|delete, got ${config.action}")
    }
}

def renderAndApply(String appName, String namespace, String imageTag, String replicas, String kubeconfigFile, String kubectlBin) {
    String generatedDir = '.generated-k8s'
    sh "mkdir -p ${generatedDir}"

    renderTemplate('k8s/deployments/app-deployment.yml.j2', "${generatedDir}/app-deployment.yml", appName, namespace, imageTag, replicas)
    renderTemplate('k8s/deployments/app-service.yml.j2', "${generatedDir}/app-service.yml", appName, namespace, imageTag, replicas)
    renderTemplate('k8s/deployments/app-ingress.yml.j2', "${generatedDir}/app-ingress.yml", appName, namespace, imageTag, replicas)

    runKubectl(kubectlBin, kubeconfigFile, "apply -f ${generatedDir}/app-deployment.yml")
    runKubectl(kubectlBin, kubeconfigFile, "apply -f ${generatedDir}/app-service.yml")
    runKubectl(kubectlBin, kubeconfigFile, "apply -f ${generatedDir}/app-ingress.yml")
}

def renderTemplate(String source, String target, String appName, String namespace, String imageTag, String replicas) {
    String content = readFile(file: source)
    content = content
        .replace('{{ APP_NAME }}', appName)
        .replace('{{ NAMESPACE }}', namespace)
        .replace('{{ IMAGE_TAG }}', imageTag)
        .replace('{{ REPLICAS }}', replicas)
    writeFile(file: target, text: content)
}

def checkRolloutWithRollback(String appName, String namespace, String kubeconfigFile, String kubectlBin) {
    try {
        runKubectl(kubectlBin, kubeconfigFile, "rollout status deployment/${appName} -n ${namespace} --timeout=180s")
    } catch (Exception ex) {
        echo "Rollout check failed, trying rollback for deployment/${appName} in ${namespace}."
        try {
            runKubectl(kubectlBin, kubeconfigFile, "rollout undo deployment/${appName} -n ${namespace}")
        } catch (Exception ignore) {
            echo 'Rollback command failed, please check cluster state manually.'
        }
        throw ex
    }
}

def runKubectl(String kubectlBin, String kubeconfigFile, String args) {
    withEnv(["KUBE_BIN=${kubectlBin}", "KUBE_CONFIG_PATH=${kubeconfigFile}"]) {
        sh(script: '''
            "$KUBE_BIN" --kubeconfig "$KUBE_CONFIG_PATH" ''' + args)
    }
}