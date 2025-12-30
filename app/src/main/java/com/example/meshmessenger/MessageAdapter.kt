package com.example.meshmessenger

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

// Меняем обычный Adapter на ListAdapter - он умнее и умеет сам считать разницу в списках
class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(MessagesComparator()) {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isSentByMe) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
            SentMessageHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is SentMessageHolder) holder.bind(message)
        else if (holder is ReceivedMessageHolder) holder.bind(message)
    }

    class SentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val body: TextView = itemView.findViewById(R.id.text_message_body)
        private val time: TextView = itemView.findViewById(R.id.text_message_time)
        private val status: TextView = itemView.findViewById(R.id.text_message_status)

        fun bind(message: Message) {
            body.text = message.text
            time.text = message.timestamp

            when (message.status) {
                MessageStatus.SENDING -> {
                    status.text = "..."
                    status.setTextColor(Color.GRAY)
                }
                MessageStatus.SENT -> {
                    status.text = "✓"
                    status.setTextColor(Color.parseColor("#FF5722"))
                }
                MessageStatus.ERROR -> {
                    status.text = "!"
                    status.setTextColor(Color.RED)
                }
            }
        }
    }

    class ReceivedMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val body: TextView = itemView.findViewById(R.id.text_message_body)
        private val time: TextView = itemView.findViewById(R.id.text_message_time)

        fun bind(message: Message) {
            body.text = message.text
            time.text = message.timestamp
        }
    }

    // Класс-сравниватель, чтобы ListAdapter знал, изменилось сообщение или нет
    class MessagesComparator : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}