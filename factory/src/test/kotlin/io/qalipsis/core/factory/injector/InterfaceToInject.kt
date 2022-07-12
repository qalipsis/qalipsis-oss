package io.qalipsis.core.factory.injector

import jakarta.inject.Singleton

interface InterfaceToInject

@Singleton
class ClassToInject : InterfaceToInject

@Singleton
class OtherClassToInject : InterfaceToInject