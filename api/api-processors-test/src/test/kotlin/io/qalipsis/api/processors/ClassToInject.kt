package io.qalipsis.api.processors

import javax.inject.Singleton

interface InterfaceToInject

@Singleton
class ClassToInject : InterfaceToInject

@Singleton
class OtherClassToInject : InterfaceToInject
