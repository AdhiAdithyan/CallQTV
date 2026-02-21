package com.softland.callqtv.interfaces

import com.softland.callqtv.data.model.CurrentDayOrder

interface OnOrderClickListener {
    fun onOrderClick(order: CurrentDayOrder)
    fun onOrderLongClick(order: CurrentDayOrder)
    fun onOrderReadyBtnClick(order: CurrentDayOrder)
    fun onOrderCancelBtnClick(order: CurrentDayOrder)
}
