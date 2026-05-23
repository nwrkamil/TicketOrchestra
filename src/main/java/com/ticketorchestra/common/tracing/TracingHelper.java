package com.ticketorchestra.common.tracing;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;

import java.util.function.Supplier;

public final class TracingHelper {

    private TracingHelper() {
    }

    public static <T> T trace(String name, Supplier<T> operation) {
        Subsegment subsegment = beginSubsegment(name);
        try {
            return operation.get();
        } catch (Exception e) {
            addException(subsegment, e);
            throw e;
        } finally {
            endSubsegment(subsegment);
        }
    }

    public static void trace(String name, Runnable operation) {
        Subsegment subsegment = beginSubsegment(name);
        try {
            operation.run();
        } catch (Exception e) {
            addException(subsegment, e);
            throw e;
        } finally {
            endSubsegment(subsegment);
        }
    }

    public static Subsegment beginSubsegment(String name) {
        try {
            if (AWSXRay.getGlobalRecorder().getTraceEntity() != null) {
                return AWSXRay.beginSubsegment(name);
            }
        } catch (Exception e) {
            // X-Ray not available
        }
        return null;
    }

    public static void endSubsegment(Subsegment subsegment) {
        if (subsegment != null) {
            try {
                AWSXRay.endSubsegment();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public static void putAnnotation(Subsegment subsegment, String key, String value) {
        if (subsegment != null) {
            subsegment.putAnnotation(key, value);
        }
    }

    public static void putMetadata(Subsegment subsegment, String namespace, String key, Object value) {
        if (subsegment != null) {
            subsegment.putMetadata(namespace, key, value);
        }
    }

    public static void addException(Subsegment subsegment, Exception e) {
        if (subsegment != null) {
            subsegment.addException(e);
        }
    }
}
