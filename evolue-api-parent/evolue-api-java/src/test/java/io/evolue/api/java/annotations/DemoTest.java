package io.evolue.api.java.annotations;

import io.evolue.api.java.components.AssertionDataSource;
import io.evolue.api.java.components.PaceStrategy;
import io.evolue.api.java.components.TestDataSource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DemoTest {

	/**
	 * Class generating data for the test to be run.
	 */
	@DataSourceDescriptor(value = "uuidGenerator")
	public static class MyDataSource implements TestDataSource<UUID> {

		@Override
		public boolean hasNext() {
			return true;
		}

		@Override
		public UUID next() {
			return UUID.randomUUID();
		}
	}

	/**
	 * Class fetching the data used for assertion.
	 */
	@DataSourceDescriptor(value = "lastCreatedDevice")
	public static class LastCreatedDeviceDataSource implements AssertionDataSource<Device> {

	}

	@PaceStrategyDescriptor("objectCreationPace")
	public static class ObjectCreationPace implements PaceStrategy {

		@Override
		public void next() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// Do nothing.
			}
		}
	}

	public static class Device {

		private final UUID uuid;

		private long savedTimestamp;

		public Device(final UUID uuid) {
			this.uuid = uuid;
		}

		public UUID getUuid() {
			return uuid;
		}

		public long getSavedTimestamp() {
			return savedTimestamp;
		}

		public Device setSavedTimestamp(final long savedTimestamp) {
			this.savedTimestamp = savedTimestamp;
			return this;
		}
	}

	@Scenario(value = "my-simple-test", machines = 10, selectors = {"zone:eu,us"})
	public static class SimpleTest {

		@TestDescriptor(
				value = "createObject",
				iterations = 0,
				paceStrategy = "objectCreationPace"
		)
		public Device createObject(@Data("uuidGenerator") final UUID deviceUuid) {
			final Device device = new Device(deviceUuid);
			// Do something here to trigger the test.
			return device;
		}

		@AssertionDescriptor(
				value = "assertObjectCreation",
				of = "createObject",
				timeout = 5,
				timeoutUnit = TimeUnit.SECONDS,
				testKeyExpression = "#returnedValue.uuid",
				dataKeyExpression = "uuid"
		)
		public long assertObjectCreation(@TestData final UUID deviceUuid, @TestReturn final Device sentDevice,
				@Data("LastCreatedDeviceDataSource") final Device actuallyCreatedDevice) {

			// Run some assertions here.
			return actuallyCreatedDevice.savedTimestamp;
		}

		@TestDescriptor(afterEachSuccess = "assertObjectCreation")
		public void useDeviceCreationTimestamp(@TestData final UUID deviceUuid,
				@AssertionData final long deviceCreationTimestamp) {

			// Do some operation with the device creation timestamp.
		}
	}
}
