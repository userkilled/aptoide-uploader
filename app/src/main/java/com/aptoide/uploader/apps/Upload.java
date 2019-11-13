package com.aptoide.uploader.apps;

public class Upload {

  private final boolean uploaded;
  private final InstalledApp installedApp;
  private final String md5;
  private final String storeName;
  private Status status;
  private boolean processed = false;

  public Upload(boolean uploaded, InstalledApp installedApp, Status status, String md5,
      String storeName) {
    this.uploaded = uploaded;
    this.installedApp = installedApp;
    this.status = status;
    this.md5 = md5;
    this.storeName = storeName;
  }

  public String getStoreName() {
    return storeName;
  }

  public boolean isUploaded() {
    return uploaded;
  }

  public InstalledApp getInstalledApp() {
    return installedApp;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public void setProcessed(boolean isProcessed) {
    processed = isProcessed;
  }

  @Override public int hashCode() {
    return installedApp.hashCode();
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Upload upload = (Upload) o;

    return installedApp.equals(upload.installedApp);
  }

  public String getMd5() {
    return md5;
  }

  public boolean isProcessed() {
    return processed;
  }

  public enum Status {
    PENDING, PROGRESS, COMPLETED, CLIENT_ERROR, NOT_EXISTENT, NO_META_DATA, DUPLICATE, OBB_MAIN, OBB_PATCH, META_DATA_ADDED, RETRY, INTELLECTUAL_RIGHTS, INFECTED, INVALID_SIGNATURE, PUBLISHER_ONLY, APP_BUNDLE, FAILED
  }
}
