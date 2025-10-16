import flet as ft
from datetime import datetime
import time
import threading


def main(page: ft.Page):
    page.title = "Mesh Messenger"
    page.theme_mode = 'light'
    page.window.width = 650
    page.window.height = 650
    current_icon = ft.Icons.LIGHT_MODE

    """Creating functions for work"""
    def mean_icon_and_theme(e):
        nonlocal current_icon
        if page.theme_mode == 'light':
            page.theme_mode = 'dark'
            current_icon = ft.Icons.DARK_MODE
        else:
            page.theme_mode = 'light'
            current_icon = ft.Icons.LIGHT_MODE
        theme_button.icon = current_icon
        page.update()

    def send_message(e):
        message = field_message.value.strip()
        if message:
            field_message.value = ''
            page.update()

    def time_update():
        while True:
            time_display.value = datetime.now().strftime("%H:%M")
            page.update()
            time.sleep(1)

    time_thread = threading.Thread(target=time_update, daemon=True)
    time_thread.start()

    """Creating buttons, icons, and time."""
    field_message = ft.TextField(label="Начать диалог", hint_text="Введите сообщение", expand=True,
                                 on_click=lambda x: send_message)

    button_1 = ft.ElevatedButton(
        'Отправить',
        on_click=lambda x: send_message
    )

    time_display = ft.Text(
        datetime.now().strftime("%H:%M"),
        size=18,
        weight=ft.FontWeight.BOLD
    )

    theme_button = ft.IconButton(
        icon=current_icon,
        on_click=mean_icon_and_theme
    )

    input_row = ft.Row(
        [field_message, button_1],
        alignment=ft.MainAxisAlignment.END
    )
    page.add(
        ft.Column([
            ft.Text("Чат с Алексеем", size=18, weight="bold"),
            ft.Container(expand=True),
            input_row,
            ft.Row([
                theme_button,
                ft.Container(expand=True),
                time_display,
                ft.Container(expand=True),
                theme_button
            ], alignment=ft.MainAxisAlignment.END)
        ], expand=True)
    )


ft.app(target=main)