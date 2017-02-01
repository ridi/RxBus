package com.ridi.books.rxbus

import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.functions.Action1
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject
import rx.subjects.Subject
import java.util.*

/**
 * Created by kering on 2017. 1. 12..
 */
object RxBus {
    private val subjects = TreeMap<Int, Subject<Any, Any>>()
    private val stickyEventMap = hashMapOf<Class<*>, Any>()
    private val errorHandlers = mutableListOf<Action1<Throwable>>()
    private val onError = Action1<Throwable> { e ->
        errorHandlers.forEach { it.call(e) }
    }

    fun addErrorHandler(handler: Action1<Throwable>) = errorHandlers.add(handler)

    fun removeErrorHandler(handler: Action1<Throwable>) = errorHandlers.remove(handler)

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    @JvmOverloads
    fun <T> subscribe(eventClass: Class<T>, callback: Action1<T>,
                      sticky: Boolean = false, priority: Int = 0,
                      scheduler: Scheduler = Schedulers.immediate()): Subscription {
        val observable = synchronized(subjects) {
            (subjects[priority] ?: run {
                val subject = SerializedSubject<Any, Any>(PublishSubject.create())
                subjects[priority] = subject
                subject
            }).ofType(eventClass)
        }
        return (if (sticky) {
            synchronized(stickyEventMap) {
                stickyEventMap[eventClass]?.let { lastEvent ->
                    observable.mergeWith(Observable.create { subscriber ->
                        subscriber.onNext(lastEvent as T)
                    })
                }
            }
        } else {
            null
        } ?: observable).observeOn(scheduler).subscribe(callback, onError)
    }

    @JvmStatic
    fun post(event: Any) = synchronized(subjects) {
        subjects.descendingMap().forEach {
            it.value.onNext(event)
        }
    }

    @JvmStatic
    fun postSticky(event: Any) {
        synchronized(stickyEventMap) {
            stickyEventMap.put(event.javaClass, event)
        }
        post(event)
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> getStickyEvent(eventClass: Class<T>): T? {
        synchronized(stickyEventMap) {
            return stickyEventMap[eventClass] as T?
        }
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> removeStickyEvent(eventClass: Class<T>): T? {
        synchronized(stickyEventMap) {
            return stickyEventMap.remove(eventClass) as T?
        }
    }
}
