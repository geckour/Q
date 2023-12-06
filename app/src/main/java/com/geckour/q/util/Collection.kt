package com.geckour.q.util

fun <T> MutableList<T>.swap(from: Int, to: Int) {
    val tmp = this[to]
    this[to] = this[from]
    this[from] = tmp
}

fun <T> List<T>.swapped(from: Int, to: Int): List<T> = this.toMutableList().apply { swap(from, to) }

fun <T> MutableList<T>.move(from: Int, to: Int) {
    val tmp = this[from]
    this.removeAt(from)
    this.add(to, tmp)
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> = this.toMutableList().apply { move(from, to) }

fun <T> List<T>.addedAll(elements: List<T>): List<T> =
    this.addedAll(0, elements)

fun <T> List<T>.addedAll(index: Int, elements: List<T>): List<T> =
    this.slice(0 until index) + elements + this.slice(index until this.size)

fun <T> List<T>.removedAt(index: Int): List<T> =
    this.slice(0 until index) + this.slice(index + 1 until this.size)

fun <T> List<T>.removedRange(from: Int, to: Int): List<T> = this - this.subList(from, to)