# DevOps Engineer Homework

## Automated CI/CD Platform with Ansible + Kubernetes + Jenkins

---

## Background

构建一个 **生产级别的 CI/CD 自动化平台**，实现从基础设施即代码（IaC）到应用全生命周期管理的完整链路。

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Ansible Control Node                  │
│  roles/                                                  │
│  ├── common/          # 基础环境、时区、依赖              │
│  ├── k8s_master/      # K8s Master 节点初始化            │
│  ├── k8s_worker/      # K8s Worker 节点加入              │
│  ├── jenkins/         # Jenkins 安装 & 初始配置           │
│  ├── jenkins_k8s/     # Jenkins ↔ K8s 集成               │
│  └── vault/           # Ansible Vault 密钥管理            │
└──────────────────────┬──────────────────────────────────┘
                       │ ansible-playbook
                       ▼
┌─────────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                      │
│                                                          │
│  ┌──────────────┐   ┌──────────────┐                    │
│  │  Master Node │   │ Worker Node  │ × N                │
│  │  API Server  │   │  kubelet     │                    │
│  │  etcd        │   │  kube-proxy  │                    │
│  │  scheduler   │   │  containerd  │                    │
│  └──────────────┘   └──────────────┘                    │
│                                                          │
│  Namespaces:                                             │
│  ├── jenkins/        # Jenkins Master Pod               │
│  ├── staging/        # 预发布环境                        │
│  └── production/     # 生产环境                         │
└──────────────────────┬──────────────────────────────────┘
                       │ kubectl / Kubernetes API
                       ▼
┌─────────────────────────────────────────────────────────┐
│                Jenkins Pipeline                          │
│  Jenkinsfile:                                           │
│  Stage 1: Checkout Code                                  │
│  Stage 2: Build Docker Image                             │
│  Stage 3: Push to Registry                               │
│  Stage 4: Deploy to K8s (create/update/delete pod)      │
│  Stage 5: Health Check & Notify                          │
└─────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
devops-homework/
├── ansible/
│   ├── inventories/
│   │   ├── hosts.yml                  # 主机清单
│   │   └── group_vars/
│   │       ├── all.yml                # 公共变量
│   │       └── all.yml.vault          # 加密敏感变量
│   ├── roles/
│   │   ├── common/
│   │   │   ├── tasks/main.yml
│   │   │   ├── handlers/main.yml
│   │   │   └── defaults/main.yml
│   │   ├── k8s_master/
│   │   │   ├── tasks/main.yml
│   │   │   ├── templates/
│   │   │   │   └── kubeadm-config.yml.j2
│   │   │   └── defaults/main.yml
│   │   ├── k8s_worker/
│   │   │   ├── tasks/main.yml
│   │   │   └── defaults/main.yml
│   │   ├── jenkins/
│   │   │   ├── tasks/main.yml
│   │   │   ├── templates/
│   │   │   │   ├── jenkins-deployment.yml.j2
│   │   │   │   └── jenkins-service.yml.j2
│   │   │   ├── files/
│   │   │   │   └── plugins.txt
│   │   │   └── defaults/main.yml
│   │   └── jenkins_k8s/
│   │       ├── tasks/main.yml
│   │       ├── templates/
│   │       │   └── jenkins-sa.yml.j2
│   │       └── defaults/main.yml
│   ├── playbooks/
│   │   ├── 01-setup-cluster.yml       # 初始化 K8s 集群
│   │   ├── 02-deploy-jenkins.yml      # 部署 Jenkins
│   │   ├── 03-integrate-jenkins-k8s.yml # 集成配置
│   │   └── site.yml                   # 一键执行入口
│   └── vault-password-file            # (gitignore)
│
├── jenkins/
│   ├── Jenkinsfile                    # 主 Pipeline
│   ├── Jenkinsfile.deploy             # 部署专用 Pipeline
│   └── shared-library/
│       └── vars/
│           ├── deployToK8s.groovy
│           └── k8sRollback.groovy
│
├── k8s/
│   ├── namespaces/
│   │   ├── staging.yml
│   │   └── production.yml
│   ├── rbac/
│   │   ├── jenkins-sa.yml             # Jenkins ServiceAccount
│   │   ├── jenkins-role.yml           # RBAC Role
│   │   └── jenkins-rolebinding.yml
│   ├── deployments/
│   │   ├── app-deployment.yml.j2      # 应用 Deployment 模板
│   │   ├── app-service.yml
│   │   └── app-ingress.yml
│   └── scripts/
│       ├── deploy.sh                  # 部署脚本
│       ├── update.sh                  # 更新脚本
│       └── delete.sh                  # 删除脚本
│
├── docker/
│   └── Dockerfile                     # 示例应用镜像
│
├── docs/
│   ├── architecture.md
│   └── runbook.md
│
├── .gitignore
├── .ansible-lint
└── README.md
```

---

## Task Description

### Task 1: Ansible Roles — Infrastructure as Code

使用 Ansible 完成以下所有组件的自动化安装，**全部封装为 roles**，**密码信息使用 Ansible Vault 加密**。

#### Role 1: `common`

所有节点基础配置：

- 关闭 swap
- 配置内核参数（`br_netfilter`、`ip_forward`）
- 安装基础依赖（curl、git、containerd）
- 统一时区、NTP 配置
- 配置 `/etc/hosts`

#### Role 2: `k8s_master`

K8s Master 节点初始化：

- 安装 `kubeadm`、`kubelet`、`kubectl`（版本锁定）
- 使用 `kubeadm init` 初始化集群
- 配置 `kubectl` kubeconfig
- 安装 CNI 网络插件（Calico 或 Flannel）
- 生成并保存 Worker join token（写入 Ansible fact）

#### Role 3: `k8s_worker`

Worker 节点加入集群：

- 安装 `kubeadm`、`kubelet`
- 使用 Master 生成的 join token 加入集群
- 验证节点状态

#### Role 4: `jenkins`

在 K8s 中部署 Jenkins：

- 创建 `jenkins` namespace
- 通过 Helm 或 kubectl 部署 Jenkins Deployment + Service
- 初始化插件列表（Kubernetes Plugin、Pipeline、Git 等）
- 配置持久化存储（PVC）
- 等待 Jenkins 就绪并输出访问地址

#### Role 5: `jenkins_k8s`

Jenkins ↔ Kubernetes 集成：

- 创建 Jenkins 专用 ServiceAccount
- 配置 RBAC（Role + RoleBinding），最小权限原则
- 自动获取 ServiceAccount Token
- 通过 Jenkins CLI 或 API 注册 K8s Cloud 配置

#### Vault 加密要求

所有敏感信息必须使用 `ansible-vault` 加密：

```yaml
# group_vars/vault.yml (加密文件示例)
vault_k8s_api_token: "eyJhbGciOiJSUzI1NiIs..."
vault_jenkins_admin_password: "SecureP@ssw0rd"
vault_registry_password: "docker-hub-token"
vault_kubeadm_token: "abcdef.0123456789abcdef"
```

使用方式：

```bash
# 加密
ansible-vault encrypt group_vars/vault.yml

# 执行时解密
ansible-playbook site.yml --vault-password-file vault-password-file
```

---

### Task 2: Jenkins Pipeline — 触发 K8s 操作

实现一个完整的 Jenkins Pipeline，支持对 K8s 的 **部署、更新、删除** 操作。

#### Pipeline 参数

```
ACTION         = deploy | update | delete
APP_NAME       = 应用名称（如 user1-site）
NAMESPACE      = staging | production
IMAGE_TAG      = latest | v1.0.0 | git commit SHA
REPLICAS       = 副本数（默认 2）
```

#### Pipeline Stages

```groovy
pipeline {
    // Stage 1: 参数校验 & 环境初始化
    // Stage 2: 拉取代码 / 构建 Docker 镜像
    // Stage 3: 推送镜像到 Registry
    // Stage 4: 根据 ACTION 执行 K8s 操作
    //   - deploy:  kubectl apply -f deployment.yml
    //   - update:  kubectl set image / rollout restart
    //   - delete:  kubectl delete deployment/svc/ingress
    // Stage 5: 健康检查（kubectl rollout status）
    // Stage 6: 通知（Slack / Email / 钉钉，任选）
}
```

#### K8s 操作封装（Shared Library）

```groovy
// vars/deployToK8s.groovy
def call(Map config) {
    // 封装 deploy/update/delete 逻辑
    // 支持蓝绿部署或滚动更新
}
```

---

### Task 3: K8s 应用全生命周期管理

通过 Jenkins Pipeline 触发，实现以下 K8s 操作：

#### 3.1 部署 Pod（Create/Deploy）

```bash
# 创建 Deployment + Service + Ingress
kubectl apply -f k8s/deployments/app-deployment.yml
kubectl apply -f k8s/deployments/app-service.yml
kubectl apply -f k8s/deployments/app-ingress.yml

# 等待部署完成
kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE}
```

#### 3.2 更新 Pod（Update/Rolling Update）

```bash
# 滚动更新镜像
kubectl set image deployment/${APP_NAME} \
  app=${REGISTRY}/${APP_NAME}:${NEW_TAG} \
  -n ${NAMESPACE}

# 或触发 rollout restart（配置更新场景）
kubectl rollout restart deployment/${APP_NAME} -n ${NAMESPACE}

# 查看更新状态
kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE}

# 支持回滚
kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}
```

#### 3.3 删除 Pod（Delete/Cleanup）

```bash
# 删除应用所有资源
kubectl delete deployment ${APP_NAME} -n ${NAMESPACE}
kubectl delete service ${APP_NAME} -n ${NAMESPACE}
kubectl delete ingress ${APP_NAME} -n ${NAMESPACE}

# 或通过 label selector 批量删除
kubectl delete all -l app=${APP_NAME} -n ${NAMESPACE}
```

---

## Deliverables

请提交 GitHub 仓库，包含：

### 1\. 完整代码

所有 Ansible roles、Jenkinsfile、K8s manifests。

### 2\. README.md 包含

- 系统架构图
- 环境要求（OS、Python、Ansible 版本）
- 快速开始步骤

```bash
# Step 1: 配置主机清单
vim ansible/inventories/hosts.yml

# Step 2: 配置并加密敏感变量
ansible-vault encrypt ansible/inventories/group_vars/vault.yml

# Step 3: 一键部署全部基础设施
ansible-playbook ansible/playbooks/site.yml \
  --vault-password-file ansible/vault-password-file

# Step 4: 在 Jenkins 触发 Pipeline
# 选择 ACTION=deploy, APP_NAME=myapp, NAMESPACE=staging
```

### 3\. Demo 说明

```bash
# 验证 K8s 集群
kubectl get nodes
kubectl get pods -A

# 验证 Jenkins
curl http://<jenkins-ip>:8080

# 触发部署
# 在 Jenkins UI 中运行 Pipeline，选择参数：
# ACTION=deploy / APP_NAME=demo-app / NAMESPACE=staging / IMAGE_TAG=v1.0.0

# 验证部署结果
kubectl get deployment demo-app -n staging
kubectl get pods -n staging -l app=demo-app

# 触发更新
# Jenkins Pipeline: ACTION=update / IMAGE_TAG=v1.1.0

# 验证滚动更新
kubectl rollout status deployment/demo-app -n staging

# 触发删除
# Jenkins Pipeline: ACTION=delete

# 验证清理
kubectl get all -n staging -l app=demo-app
```

---

## Bonus (Optional, 加分项)

| 项目 | 说明 |
| --- | --- |
| **Helm Chart 封装** | 将应用打包为 Helm Chart，Jenkins 通过 `helm upgrade --install` 部署 |
| **蓝绿部署** | 实现 Blue/Green 切换，Ingress 流量 100% 切换后再删除旧版本 |
| **Vault 集成** | 使用 HashiCorp Vault 替代 Ansible Vault 管理运行时密钥 |
| **监控接入** | 部署 Prometheus + Grafana，监控 Jenkins Job 和 Pod 状态 |
| **自动回滚** | 健康检查失败时 Pipeline 自动触发 `kubectl rollout undo` |
| **多环境支持** | staging 自动部署，production 需人工 Approve（Jenkins Input Step） |
| **GitOps 模式** | 使用 ArgoCD 替代 Jenkins 直接 kubectl，实现声明式部署 |

---

## Evaluation Criteria

| 维度 | 权重 | 评估点 |
| --- | --- | --- |
| **Ansible 工程质量** | 30% | roles 结构清晰、幂等性、Vault 正确使用 |
| **K8s 理解深度** | 25% | RBAC 最小权限、资源模板设计、滚动更新策略 |
| **Jenkins Pipeline** | 25% | 参数化、错误处理、Shared Library 抽象 |
| **文档 & 可运行性** | 15% | README 清晰、一键部署成功率 |
| **安全意识** | 5% | 密钥无明文、最小权限、镜像安全 |

---

## Notes

- **禁止**将任何密码、Token、私钥明文提交到 Git（使用 `.gitignore` + `ansible-vault`）
- Ansible roles 需保证 **幂等性**（多次执行结果一致）
- K8s Deployment 需配置 **资源限制**（`resources.requests` / `resources.limits`）
- Jenkins ServiceAccount 遵循 **最小权限原则**，不得使用 `cluster-admin`
- 欢迎使用 AI 工具，但需对生成代码负责并能解释每一行的作用
