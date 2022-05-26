package io.qalipsis.core.head.utils

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.model.Sort
import io.qalipsis.core.head.jdbc.entity.CampaignEntity

object SortingUtil {
    /**
     * Sorts results from CampaignRepository
     * @Param sort is a name of the property to sort. Could also include suffixes ':desc' or ':asc' to define
     * the sorting order. By default, asc order is used.
     */
    @JvmStatic
    internal fun sort1(sort: String, campaignList: List<CampaignEntity>): List<CampaignEntity> {
        val sortProperty = BeanIntrospection.getIntrospection(CampaignEntity::class.java).beanProperties
            .firstOrNull {
                it.name == sort.trim().split(":").get(0)
            }
        val sortOrder = sort.trim().split(":").last()
        return if ("desc" == sortOrder) {
            campaignList.sortedBy { sortProperty?.get(it) as Comparable<Any> }.reversed()
        } else {
            campaignList.sortedBy { sortProperty?.get(it) as Comparable<Any> }
        }
    }

    @JvmStatic
    internal fun sort(sort: String): Sort? {
        val sortProperty = BeanIntrospection.getIntrospection(CampaignEntity::class.java).beanProperties
            .firstOrNull {
                it.name == sort.trim().split(":").get(0)
            }
        val sortOrder = sort.trim().split(":").last()
        return if ("desc" == sortOrder) {
            Sort.of(Sort.Order.desc(sortProperty?.name))
        } else {
            Sort.of(Sort.Order.asc(sortProperty?.name))
        }
    }
}