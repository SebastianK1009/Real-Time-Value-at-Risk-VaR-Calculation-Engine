{{/*
Generate the name of the chart (truncated to 63 chars for Kubernetes limits)
*/}}
{{- define "ingestion-service.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Generate the full name for resources (uses release name)
*/}}
{{- define "ingestion-service.fullname" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Generate standard labels for all resources
*/}}
{{- define "ingestion-service.labels" -}}
app: {{ include "ingestion-service.name" . }}
release: {{ .Release.Name }}
{{- end }}

{{/*
Generate selector labels (subset of labels used for pod selection)
*/}}
{{- define "ingestion-service.selectorLabels" -}}
app: {{ include "ingestion-service.name" . }}
release: {{ .Release.Name }}
{{- end }}
