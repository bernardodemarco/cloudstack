package org.apache.cloudstack.tosca.functions;

public class ToscaValueProducerFunctions {
    public static class GetInput implements ToscaFunction.ToscaValueProducerFunction {
        private final String inputName;

        public GetInput(String inputName) {
            this.inputName = inputName;
        }

        @Override
        public Object produce(Object... args) {
            return null;
        }
    }
}
