"""
URL configuration for config project.

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/6.1/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""

from django.http import JsonResponse
from django.urls import path


from api.views import (
    health,
    gets_all_posts,
    my_posts,
    get_question_posts,
)


from api.profile_views import (
    initialize_profile,
    my_profile,
    update_my_profile,
)
from django.views.decorators.csrf import csrf_exempt
from api.registration_views import get_registration_state
from api.post_views import create_post

from api.question_answer_views import (
    save_question_answer,
    get_my_question_answer,
    get_question_results,
)



#診断関係のものをインポート
from api.diagnosis_views import (
    get_diagnosis_questions,
    submit_diagnosis,
    get_latest_diagnosis,
    get_diagnosis_history,
    get_diagnosis_by_id,
    skip_initial_diagnosis,
)

from api.question_views import (
    get_today_question,
    get_archived_questions,
)

from api.post_like_views import post_like

from api.post_comment_views import post_comments


@csrf_exempt
def my_profile_route(request):
    if request.method == "GET":
        return my_profile(request)

    if request.method == "PATCH":
        return update_my_profile(request)

    response = JsonResponse(
        {"error": "Method not allowed"},
        status=405,
    )
    response["Allow"] = "GET, PATCH"
    return response


#postの区別がつくように
@csrf_exempt
def posts_route(request):
    if request.method == "GET":
        return gets_all_posts(request)

    if request.method == "POST":
        return create_post(request)

    response = JsonResponse(
        {"error": "Method not allowed"},
        status=405,
    )
    response["Allow"] = "GET, POST"
    return response


urlpatterns = [
    path("", health),
    path("health", health),
    path("posts", posts_route),

    path("users/me", my_profile_route), #上の判別関数に飛んで、通信プロトコルの種類を分別する
    path("users/me/initialize", initialize_profile),
    path("users/me/registration", get_registration_state),
    path("users/me/posts", my_posts),

    #ライフデザイン診断用
    path("diagnoses/questions", get_diagnosis_questions),
    path("diagnoses", submit_diagnosis),
    path("diagnoses/latest", get_latest_diagnosis),
    path("diagnoses/history", get_diagnosis_history),
    path("diagnoses/<str:diagnosis_id>", get_diagnosis_by_id),

    # 初回診断スキップ
    path("users/me/diagnosis-skip", skip_initial_diagnosis),

    # 今日の問い
    path("questions/today", get_today_question),

    
    path("questions/archive", get_archived_questions),

    
    # 今日の問いへの選択回答保存
    path(
        "questions/<str:question_id>/answer",
        save_question_answer,
    ),

    
    # 自分が選択した回答
    path(
        "questions/<str:question_id>/answer/me",
        get_my_question_answer,
    ),

    # 選択肢別の回答数・割合
    path(
        "questions/<str:question_id>/results",
        get_question_results,
    ),

    
    path(
        "questions/<str:question_id>/posts",
        get_question_posts,
    ),


    # 投稿へのいいね追加・解除
    path(
        "posts/<str:post_id>/like",
        post_like,
    ),


    # 投稿へのコメント作成・取得
    path(
        "posts/<str:post_id>/comments",
        post_comments,
    ),






]

