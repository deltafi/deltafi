package org.deltafi.common.trace;

@SuppressWarnings("unused")
public class ZipkinConfig {

    private boolean enabled = true;
    private long initialDelayInMilli = 500L;
    private long sendFrequencyInMilli = 500L;
    private int maxBatchSize = 10_000;
    private String url;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getInitialDelayInMilli() {
        return initialDelayInMilli;
    }

    public void setInitialDelayInMilli(long initialDelayInMilli) {
        this.initialDelayInMilli = initialDelayInMilli;
    }

    public long getSendFrequencyInMilli() {
        return sendFrequencyInMilli;
    }

    public void setSendFrequencyInMilli(long sendFrequencyInMilli) {
        this.sendFrequencyInMilli = sendFrequencyInMilli;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
