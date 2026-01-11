package cloud.cholewa.data.device.service;

class DeviceConfigurationValidatorTest {

//    @ParameterizedTest(name = "{0}")
//    @MethodSource("variousConfigurationExamples")
//    void should_pass_if_only_one_configuration_provided(
//        final String name,
//        final DeviceConfiguration deviceConfiguration,
//        final boolean expected
//    ) {
//        assertEquals(DeviceConfigurationValidator.isOnlyOneConfigurationProvided(deviceConfiguration), expected);
//    }
//
//    private static Stream<Arguments> variousConfigurationExamples() {
//        return Stream.of(
//            Arguments.of(
//                "only eaton configuration",
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder().build())
//                    .build(),
//                true
//            ),
//            Arguments.of(
//                "only shelly configuration",
//                DeviceConfiguration.builder()
//                    .shellyConfiguration(ShellyConfiguration.builder().build())
//                    .build(),
//                true
//            )
//        );
//    }
//
//    @Test
//    void should_throw_invalid_configuration_exception_when_more_configurations_provided() {
//        DeviceConfiguration configuration = DeviceConfiguration.builder()
//            .eatonConfiguration(EatonConfiguration.builder().build())
//            .shellyConfiguration(ShellyConfiguration.builder().build())
//            .build();
//
//        Assertions.assertThrows(InvalidDeviceConfigurationException.class,
//            () -> DeviceConfigurationValidator.isOnlyOneConfigurationProvided(configuration),
//            CustomErrorDescription.TOO_MANY_CONFIGURATIONS.getDescription()
//        );
//    }
//
//    @Test
//    void should_throw_invalid_configuration_exception_when_no_configurations_provided() {
//        DeviceConfiguration configuration = DeviceConfiguration.builder()
//            .build();
//
//        Assertions.assertThrows(InvalidDeviceConfigurationException.class,
//            () -> DeviceConfigurationValidator.isOnlyOneConfigurationProvided(configuration),
//            CustomErrorDescription.MISSING_DEVICE_CONFIGURATION.getDescription()
//        );
//    }
//
//    @ParameterizedTest(name = "{0}")
//    @MethodSource("source_of_eaton_configurations")
//    void should_check_if_provided_device_configuration_is_valid_eaton_device(
//        final String name,
//        final DeviceConfiguration configuration,
//        final boolean isEaton
//    ) {
//
//        assertEquals(DeviceConfigurationValidator.isValidEatonDevice(configuration), isEaton);
//    }
//
//    private static Stream<Arguments> source_of_eaton_configurations() {
//        return Stream.of(
//            Arguments.of(
//                "eaton configuration is null",
//                DeviceConfiguration.builder().build(),
//                false
//            ),
//            Arguments.of(
//                "invalid data point is less than minimum",
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .dataPoint(0)
//                        .eatonGateway(EatonGateway.fromValue("blinds"))
//                        .build())
//                    .build(),
//                false
//            ),
//            Arguments.of(
//                "invalid data point is more than minimum",
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .dataPoint(100)
//                        .eatonGateway(EatonGateway.fromValue("lights"))
//                        .build())
//                    .build(),
//                false
//            ),
//            Arguments.of(
//                "invalid data point is null",
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .build())
//                    .build(),
//                false
//            ),
//            Arguments.of(
//                "invalid eaton gateway is null",
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .dataPoint(1)
//                        .build())
//                    .build(),
//                false
//            ),
//            Arguments.of(
//                "data point valid and eaton gateway is uppercase blinds",
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .dataPoint(1)
//                        .eatonGateway(EatonGateway.fromValue("BLINDS"))
//                        .build())
//                    .build(),
//                true
//            ),
//            Arguments.of(
//                "data point valid and eaton gateway is mixed case lights",
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .dataPoint(99)
//                        .eatonGateway(EatonGateway.fromValue("liGHtS"))
//                        .build())
//                    .build(),
//                true
//            )
//        );
//    }
}
