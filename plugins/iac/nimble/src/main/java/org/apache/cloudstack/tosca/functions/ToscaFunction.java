package org.apache.cloudstack.tosca.functions;

public interface ToscaFunction {
    interface ToscaBooleanFunction extends ToscaFunction {
        boolean evaluate(Object arg);
    }

    interface ToscaValueProducerFunction extends ToscaFunction {
        Object produce(Object... args);
    }
}
