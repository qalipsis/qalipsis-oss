package io.qalipsis.api.processors.scenario

import jakarta.inject.Singleton

interface InterfaceToInject

@Singleton
class ClassToInject : InterfaceToInject

@Singleton
class OtherClassToInject : InterfaceToInject
