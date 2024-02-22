package io.qalipsis.core.factory.meters

import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot

class MeterSnapShotImpl<T : Meter<T>>(override val meter: T, override val measurements: Collection<Measurement>) : MeterSnapshot<T>