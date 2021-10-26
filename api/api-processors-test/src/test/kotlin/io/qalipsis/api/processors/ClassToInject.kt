package io.qalipsis.api.processors

import jakarta.inject.Singleton

interface InterfaceToInject

@Singleton
class ClassToInject : InterfaceToInject

@Singleton
class OtherClassToInject : InterfaceToInject
