package io.evolue.api.java.annotations;

import io.evolue.api.java.aware.SelectorAware;
import io.evolue.api.java.aware.TagsAware;
import io.evolue.api.java.aware.TargetAware;
import io.evolue.api.java.components.AssertionDataSource;
import io.evolue.api.java.components.Converter;
import io.evolue.api.java.components.PaceStrategy;
import io.evolue.api.java.components.Request;
import io.evolue.api.java.components.Response;
import io.evolue.api.java.components.TestDataSource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DemoTest {

	@ConverterDescriptor
	public static class DeviceSerializer implements Converter<Device, byte[]> {

	}

	/**
	 * Class generating data for the test to be run.
	 */
	@DataSourceDescriptor(value = "uuidGenerator-us")
	public static class MyUsDataSource implements TestDataSource<UUID>, TargetAware {

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
	 * Class generating data for the test to be run.
	 */
	@DataSourceDescriptor(value = "uuidGenerator-eu")
	public static class MyEuDataSource implements TestDataSource<UUID>, TargetAware {

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
	public static class LastCreatedDeviceDataSource implements AssertionDataSource<Device>, TagsAware, SelectorAware {

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

	public abstract static class SimpleTest {

		@Action(
				value = "createObject",
				iterations = 0,
				paceStrategy = "objectCreationPace",
				selectors = {"zone:eu"}
		)
		public Request createEuObject(@Data("uuidGenerator-eu") final UUID deviceUuid) {
			final Device device = new Device(deviceUuid);
			/*return HTTP.scheme("https")
					.host("")
					.header("","")
					.post("/", device)
					.loadJs(true);
					.responseAssertion(response -> {});*/
			return null;
		}

		@Action(
				value = "createObject",
				iterations = 0,
				paceStrategy = "objectCreationPace",
				selectors = {"zone:us"}
		)
		public Request createUsObject(@Data("uuidGenerator-us") final UUID deviceUuid) {
			final Device device = new Device(deviceUuid);
			/*return HTTP.scheme("https")
					.host("")
					.header("","")
					.post("/", device)
					.loadJs(true);
					.responseAssertion(response -> {});*/
			return null;
		}

		@Assertion(
				value = "assertObjectCreation",
				of = "createObject",
				timeout = 5,
				timeoutUnit = TimeUnit.SECONDS,
				testKeyExpression = "#returnedValue.uuid",
				dataKeyExpression = "uuid"
		)
		public long assertObjectCreation(@TestData final UUID deviceUuid, @TestReturn final Device sentDevice,
				final Response response, final Request request,
				@Data("LastCreatedDeviceDataSource") final Device actuallyCreatedDevice) {

			// Run some assertions here.
			return actuallyCreatedDevice.savedTimestamp;
		}

		@Action(afterEachSuccess = "assertObjectCreation")
		public void useDeviceCreationTimestamp(@TestData final UUID deviceUuid,
				@AssertionResult final long deviceCreationTimestamp) {

			// Do some operation with the device creation timestamp.
		}
	}

	@Scenario(
			value = "my-simple-test",
			machines = 10,
			selectors = {"zone:eu"},
			rampUpStrategy = "",
			sessionHolder = ""
	)
	public static class EuSimpleTest extends SimpleTest {

	}

	@Scenario(
			value = "my-simple-test",
			machines = 100,
			selectors = {"zone:us"},
			rampUpStrategy = "",
			sessionHolder = ""
	)
	public static class UsSimpleTest extends SimpleTest{}
}
