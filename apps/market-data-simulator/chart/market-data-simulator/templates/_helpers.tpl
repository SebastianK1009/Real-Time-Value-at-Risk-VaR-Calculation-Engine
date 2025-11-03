{{/*
Generate the name of the chart (truncated to 63 chars for Kubernetes limits)
*/}}
{{- define "market-data-simulator.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }} # Get chart name, truncate to 63 chars
{{- end }}

{{/*
Generate the full name for resources (uses release name)
*/}}
{{- define "market-data-simulator.fullname" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" }} # Get release name for resources
{{- end }}

{{/*
Generate standard labels for all resources
*/}}
{{- define "market-data-simulator.labels" -}}
app: {{ include "market-data-simulator.name" . }} # Standard labels
release: {{ .Release.Name }}
{{- end }}

{{/*
Generate selector labels (subset of labels used for pod selection)
*/}}
{{- define "market-data-simulator.selectorLabels" -}}
app: {{ include "market-data-simulator.name" . }} # Labels for pod selection
release: {{ .Release.Name }}
{{- end }}
