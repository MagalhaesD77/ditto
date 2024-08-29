# Eclipse Ditto :: MEC APP

Conversion of the Ditto Helm chart to a MEC APP.

## 1. Use the Ditto Helm chart locally

### Package the chart

```bash
mkdir -p helm
cp -r ../helm/ditto helm
helm dependency update helm/
tar -C helm -czvf helm.tar.gz ditto
```

### Package the MEC APP

```bash
tar -czvf ditto-mec-app.tar.gz ditto-appd.yaml helm.tar.gz
```
